package com.razor.api.service;

import com.razor.api.dto.PublicBookingDtos;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.Barbershop;
import com.razor.api.model.BarbershopSite;
import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.PhoneNumbers;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agendamento online pela pagina publica do site ({@code /b/{slug}/agendar}) — sem login.
 *
 * <p><b>Mesmo motor do bot.</b> A grade vem do {@link AgendaAvailabilityService} (expediente,
 * dias abertos, bloqueios, duracao do servico, capacidade) e a gravacao passa pelo
 * {@link AgendaBookingService#agendar}, que checa de novo o horario sob trava e dispara os mesmos efeitos
 * colaterais de um agendamento do bot (lembrete 1h antes, pedido de avaliacao, ponto de
 * fidelidade). Uma regra nova de agendamento entra la e vale aqui sem ninguem lembrar.
 *
 * <p><b>Tenant.</b> A barbearia vem do slug da URL e e definida no {@link TenantContext} so
 * durante a chamada ({@link #comTenant}) — os servicos da agenda sao escopados por ele. Nunca
 * confiamos num tenant que ja estivesse no contexto: um dono logado abrindo o site de OUTRA
 * barbearia nao pode agendar na dele.
 *
 * <p><b>Confirmacao do telefone — decisao.</b> O agendamento e criado na hora e o cliente recebe
 * no WhatsApp a confirmacao com a instrucao de cancelar ("responda CANCELAR"); o dono recebe o
 * aviso de que entrou um agendamento pelo site. Descartamos o fluxo PENDENTE + "responda SIM"
 * porque (1) exigiria um estado novo na agenda que bot, painel, ocupacao e relatorios teriam de
 * aprender a ignorar, e (2) cada horario pendente seguraria uma cadeira sem garantia de
 * confirmacao. O abuso e contido por: limite por IP (RateLimitFilter), honeypot, validacao do
 * telefone, teto de agendamentos futuros por telefone e a propria mensagem — quem teve o numero
 * usado por outra pessoa fica sabendo na hora e cancela respondendo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicBookingService {

    /** Ate quantos dias a frente o site aceita (hoje incluso). */
    static final int MAX_DAYS_AHEAD = 30;
    /**
     * Teto de horarios FUTUROS por telefone aceitos pelo site. Freio anti-abuso (alguem lotando a
     * agenda com o mesmo numero), independente de qualquer regra de negocio do dono.
     */
    static final int MAX_FUTUROS_POR_TELEFONE = 3;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    /** Letras (com acento), espaco, ponto, apostrofo e hifen; comeca com letra. */
    private static final Pattern NOME = Pattern.compile("^\\p{L}[\\p{L} .'-]{1,79}$");
    private static final String INDISPONIVEL = "Agendamento online indisponível.";

    private final BarbershopRepository barbershopRepository;
    private final BarbershopSiteRepository siteRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final BarbershopSettingsService settingsService;
    private final AgendaAvailabilityService availabilityService;
    private final AgendaBookingService bookingService;
    private final CustomerService customerService;
    private final TenantAdminService tenantAdminService;
    private final WhatsAppService whatsAppService;
    private final BookingLock bookingLock;

    private record Loja(Barbershop shop, BarbershopSite site) {
    }

    /**
     * Barbearia ativa, com site publicado E agendamento online ligado. Os tres "nao" respondem o
     * mesmo 404 — mesmo criterio do {@link PublicSiteService}: nao contar a estranhos o que existe.
     */
    private Loja resolver(String slug) {
        Barbershop shop = barbershopRepository.findBySlug(slug)
                .filter(Barbershop::isActive)
                .orElseThrow(() -> new NotFoundException(INDISPONIVEL));
        BarbershopSite site = siteRepository.findById(shop.getId())
                .filter(BarbershopSite::isPublished)
                .filter(BarbershopSite::isOnlineBookingEnabled)
                .orElseThrow(() -> new NotFoundException(INDISPONIVEL));
        return new Loja(shop, site);
    }

    public PublicBookingDtos.Options options(String slug) {
        Loja loja = resolver(slug);
        UUID id = loja.shop().getId();
        var cfg = settingsService.resolveFor(id);

        List<ServiceOffering> catalogo = serviceRepository.findByBarbershopIdAndActiveTrue(id);
        List<PublicBookingDtos.ServiceOption> services = catalogo.stream()
                .sorted(Comparator.comparing(ServiceOffering::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new PublicBookingDtos.ServiceOption(
                        s.getId(), s.getName(), duracao(s),
                        loja.site().isShowPrices() ? s.getPrice() : null))
                .toList();
        List<PublicBookingDtos.ProfessionalOption> pros = professionalRepository.findByBarbershopIdAndActiveTrue(id).stream()
                .sorted(Comparator.comparing(Professional::getName, String.CASE_INSENSITIVE_ORDER))
                // Quais servicos cada barbeiro faz (V37): a pagina so oferece, para o servico
                // escolhido, quem de fato o atende.
                .map(p -> new PublicBookingDtos.ProfessionalOption(p.getId(), p.getName(),
                        catalogo.stream()
                                .filter(s -> !ServiceCapability.qualified(List.of(p), s.getName(), catalogo).isEmpty())
                                .map(ServiceOffering::getId)
                                .toList()))
                .toList();

        return new PublicBookingDtos.Options(
                loja.shop().getSlug(),
                cfg.displayName() != null ? cfg.displayName() : loja.shop().getName(),
                loja.site().getThemeMode(),
                loja.site().getAccentColor(),
                loja.site().getLogoUrl(),
                loja.site().getWhatsappNumber(),
                MAX_DAYS_AHEAD,
                cfg.workingWeekdays().stream().map(DayOfWeek::getValue).sorted().toList(),
                services,
                pros);
    }

    /** Horarios livres do dia. Data fora da janela (passado ou alem do limite) devolve lista vazia. */
    public PublicBookingDtos.Slots slots(String slug, UUID serviceId, UUID professionalId, LocalDate date) {
        Loja loja = resolver(slug);
        UUID id = loja.shop().getId();
        ServiceOffering service = servico(id, serviceId);
        validarProfissional(id, professionalId, service);
        if (!dentroDaJanela(date)) return new PublicBookingDtos.Slots(date, List.of());

        List<String> livres = comTenant(id, () -> availabilityService.horariosLivres(date, duracao(service),
                        professionalId, aptos(id, service)))
                .stream().map(AppointmentDates::formatTime).toList();
        return new PublicBookingDtos.Slots(date, livres);
    }

    public PublicBookingDtos.Confirmation book(String slug, PublicBookingDtos.Request req) {
        Loja loja = resolver(slug);
        UUID id = loja.shop().getId();

        // Honeypot: resposta generica, sem dizer o porque — dizer ensinaria o robo a desviar.
        if (req.website() != null && !req.website().isBlank()) {
            log.warn("[SITE-AGENDA] Honeypot preenchido no site de {} — pedido descartado.", slug);
            throw new IllegalArgumentException("Não foi possível concluir o agendamento.");
        }

        String nome = normalizarNome(req.name());
        String telefone = normalizarTelefone(req.phone())
                .orElseThrow(() -> new IllegalArgumentException("Informe um WhatsApp válido, com DDD."));
        ServiceOffering service = servico(id, req.serviceId());
        Professional profissional = validarProfissional(id, req.professionalId(), service);
        LocalTime hora;
        try {
            hora = LocalTime.parse(req.time());
        } catch (Exception e) {
            throw new IllegalArgumentException("Horário inválido.");
        }
        if (!dentroDaJanela(req.date())) {
            throw new IllegalArgumentException("Escolha uma data entre hoje e os próximos " + MAX_DAYS_AHEAD + " dias.");
        }

        Appointment salvo = comTenant(id, () -> {
            // O horario tem de estar na grade que a pagina mostraria AGORA (expediente, dia aberto,
            // passado). Sem isso um POST montado a mao marcaria 03:00 de um domingo.
            if (!availabilityService.horariosLivres(req.date(), duracao(service), req.professionalId(),
                    aptos(id, service)).contains(hora)) {
                throw new BookingConflictException("Esse horário não está mais disponível. Escolha outro.");
            }

            // Teto anti-abuso, cadastro e gravacao sob a trava da agenda, ate o commit: dois
            // envios simultaneos do mesmo telefone nao passam juntos pelo teto.
            Appointment app = bookingLock.run(id, () -> {
                if (futurosDoTelefone(id, telefone) >= MAX_FUTUROS_POR_TELEFONE) {
                    throw new IllegalArgumentException("Este WhatsApp já tem horários marcados. "
                            + "Para marcar mais, fale com a barbearia.");
                }
                customerService.findOrCreateCustomer(telefone, nome);
                return bookingService.agendar(new AgendaBookingService.NovoAgendamento(
                                nome, telefone, req.date(), hora, service.getName(), duracao(service),
                                req.professionalId(), req.professionalId() != null, AppointmentSource.SITE))
                        .orElseThrow(() -> new BookingConflictException(
                                "Esse horário acabou de ser ocupado. Escolha outro."));
            });

            // Mensagens so depois do commit (o bookingLock.run acima ja confirmou a gravacao).
            String nomeBarbeiro = nomeDoBarbeiro(id, app.getProfessionalId(), profissional);
            avisarCliente(app, nomeBarbeiro);
            avisarDono(app, nomeBarbeiro);
            return app;
        });

        return new PublicBookingDtos.Confirmation(
                settingsService.resolveFor(id).displayName(),
                salvo.getService(),
                nomeDoBarbeiro(id, salvo.getProfessionalId(), profissional),
                salvo.getAppointmentDate(),
                AppointmentDates.formatTime(salvo.getAppointmentTime()));
    }

    // ---------------------------------------------------------------------------------------

    private ServiceOffering servico(UUID shop, UUID serviceId) {
        if (serviceId == null) throw new IllegalArgumentException("Escolha um serviço.");
        return serviceRepository.findById(serviceId)
                .filter(s -> shop.equals(s.getBarbershopId()) && s.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Serviço indisponível."));
    }

    /**
     * {@code null} = sem preferencia. Barbeiro de outra barbearia, inativo ou que nao faz o servico
     * pedido (V37) e recusado.
     */
    private Professional validarProfissional(UUID shop, UUID professionalId, ServiceOffering service) {
        if (professionalId == null) return null;
        Professional pro = professionalRepository.findById(professionalId)
                .filter(p -> shop.equals(p.getBarbershopId()) && p.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Profissional indisponível."));
        if (service != null && !pro.servesAllServices() && ServiceCapability.qualified(List.of(pro), service.getName(),
                serviceRepository.findByBarbershopIdAndActiveTrue(shop)).isEmpty()) {
            throw new IllegalArgumentException(pro.getName() + " não faz " + service.getName() + ". Escolha outro profissional.");
        }
        return pro;
    }

    /**
     * Barbeiros ativos que fazem o servico — so eles contam como cadeira livre na grade.
     * {@code null} quando ninguem tem restricao configurada (todos fazem tudo).
     */
    private List<UUID> aptos(UUID shop, ServiceOffering service) {
        List<Professional> ativos = professionalRepository.findByBarbershopIdAndActiveTrue(shop);
        if (ativos == null || ativos.isEmpty() || ativos.stream().allMatch(Professional::servesAllServices)) return null;
        return ServiceCapability.qualified(ativos, service.getName(), serviceRepository.findByBarbershopIdAndActiveTrue(shop))
                .stream().map(Professional::getId).toList();
    }

    private String nomeDoBarbeiro(UUID shop, UUID atribuido, Professional pedido) {
        if (atribuido == null) return null;
        if (pedido != null && pedido.getId().equals(atribuido)) return pedido.getName();
        return professionalRepository.findById(atribuido)
                .filter(p -> shop.equals(p.getBarbershopId()))
                .map(Professional::getName)
                .orElse(null);
    }

    private static int duracao(ServiceOffering s) {
        Integer d = s.getDurationMinutes();
        return d != null && d > 0 ? d : AgendaAvailabilityService.SLOT_MINUTES;
    }

    private static boolean dentroDaJanela(LocalDate date) {
        LocalDate hoje = AppointmentDates.today();
        return date != null && !date.isBefore(hoje) && date.isBefore(hoje.plusDays(MAX_DAYS_AHEAD));
    }

    /**
     * Horarios por vir do telefone — mesma regra (e mesma consulta) do limite da barbearia, em
     * qualquer grafia do numero (com/sem o nono digito).
     */
    private long futurosDoTelefone(UUID shop, String telefone) {
        return bookingService.upcomingFor(shop, telefone).size();
    }

    static String normalizarNome(String raw) {
        String nome = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (!NOME.matcher(nome).matches()) {
            throw new IllegalArgumentException("Informe seu nome (só letras).");
        }
        return nome;
    }

    /**
     * WhatsApp brasileiro no formato que o bot grava (DDI 55 + DDD + numero, so digitos): o mesmo
     * cliente que agenda pelo site e conversa pelo WhatsApp cai no mesmo cadastro.
     */
    static Optional<String> normalizarTelefone(String raw) {
        // Formato canonico unico (o mesmo do painel): ver PhoneNumbers.canonicalBrazilian.
        return PhoneNumbers.canonicalBrazilian(raw);
    }

    private void avisarCliente(Appointment app, String nomeBarbeiro) {
        String loja = settingsService.resolve().displayName();
        String primeiroNome = app.getCustomerName().split(" ")[0];
        String msg = "Olá, " + primeiroNome + "! ✅ Seu horário na *" + loja + "* está confirmado:\n\n"
                + "📅 " + diaPorExtenso(app.getAppointmentDate()) + " às *"
                + AppointmentDates.formatTime(app.getAppointmentTime()) + "*\n"
                + "✂️ " + app.getService() + (nomeBarbeiro != null ? " com " + nomeBarbeiro : "") + "\n\n"
                + "Você recebe um lembrete 1 hora antes. Precisa cancelar ou remarcar? "
                + "É só responder *cancelar* aqui.\n\n"
                + "_Não foi você que agendou? Responda *cancelar* que a gente desfaz._";
        try {
            whatsAppService.sendTextMessage(app.getCustomerPhone(), msg);
        } catch (Exception e) {
            // O agendamento ja esta gravado; a mensagem e cortesia. Fica no log para o dono.
            log.error("[SITE-AGENDA] Confirmacao nao enviada para {}", app.getCustomerPhone(), e);
        }
    }

    private void avisarDono(Appointment app, String nomeBarbeiro) {
        String msg = "🌐 *Novo agendamento pelo site*\n"
                + "👤 " + app.getCustomerName() + " (" + app.getCustomerPhone() + ")\n"
                + "📅 " + diaPorExtenso(app.getAppointmentDate()) + " às "
                + AppointmentDates.formatTime(app.getAppointmentTime()) + "\n"
                + "✂️ " + app.getService() + (nomeBarbeiro != null ? " com " + nomeBarbeiro : "");
        for (String adm : tenantAdminService.adminPhones()) {
            try {
                whatsAppService.sendTextMessage(adm, msg);
            } catch (Exception e) {
                log.error("[SITE-AGENDA] Aviso ao admin {} nao enviado", adm, e);
            }
        }
    }

    private static String diaPorExtenso(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR) + ", " + AppointmentDates.formatDate(d);
    }

    /**
     * Roda com a barbearia do slug no {@link TenantContext} e restaura o que havia antes (o filtro
     * de JWT pode ter definido o tenant do dono logado que esta so visitando o site).
     */
    private static <T> T comTenant(UUID shop, Supplier<T> fn) {
        UUID anterior = TenantContext.isSet() ? TenantContext.getCurrent() : null;
        TenantContext.set(shop);
        try {
            return fn.get();
        } finally {
            if (anterior != null) TenantContext.set(anterior);
            else TenantContext.clear();
        }
    }
}
