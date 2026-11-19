package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.Professional;
import com.razor.api.model.WaitlistEntry;
import com.razor.api.model.WaitlistOffer;
import com.razor.api.repository.WaitlistOfferRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lista de espera automatica (V47).
 *
 * <ol>
 *   <li><b>Entrar</b> ({@link #join}): a IA manda o desejo em texto livre ("sexta de manhã",
 *       servico, barbeiro); vira data/dia da semana/periodo estruturados ({@link WaitlistWish}).</li>
 *   <li><b>Vaga liberada</b> ({@link #offerFreedSlot}): qualquer cancelamento (cliente pelo bot,
 *       painel, "2" da confirmacao, liberacao automatica) oferece o horario a <b>uma</b> pessoa por
 *       vez, em ordem de chegada, entre as que querem aquele dia/periodo/barbeiro e cujo servico cabe
 *       ali. Horario no passado (ou em menos de {@value #MIN_LEAD_MINUTES} min) nunca e oferecido.</li>
 *   <li><b>Resposta</b> ({@link #handleReply}): "sim" marca pela porta unica
 *       ({@link AgendaBookingService#agendar}, sob o BookingLock); "nao" passa para o proximo.</li>
 *   <li><b>Prazo</b> ({@link #expireDue}): sem resposta no prazo (padrao 15 min, aba Automacoes),
 *       a oferta expira e vai para o proximo.</li>
 * </ol>
 * A oferta vive no banco ({@code waitlist_offers}): o prazo sobrevive a restart e a mesma vaga
 * nunca e oferecida duas vezes a mesma pessoa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistOfferService {

    /** Vaga que comeca em menos que isto nao e oferecida: nao da tempo de responder e chegar. */
    static final int MIN_LEAD_MINUTES = 15;
    /** Pedido sem data exata vale por isto; depois sai da fila sozinho (ninguem lembra que pediu). */
    static final int STALE_DAYS = 30;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final WaitlistRepository waitlistRepository;
    private final WaitlistOfferRepository offerRepository;
    private final AgendaBookingService bookingService;
    private final AgendaAvailabilityService availabilityService;
    private final ProfessionalAssignmentService assignmentService;
    private final ProfessionalService professionalService;
    private final AutomationSettingsService automationSettingsService;
    private final WhatsAppMessenger messenger;

    /** A vaga liberada: dia, hora, servico do horario cancelado e barbeiro (nulo = sem barbeiro). */
    public record Slot(LocalDate date, LocalTime time, String service, UUID professionalId) {
        static Slot of(WaitlistOffer o) {
            return new Slot(o.getSlotDate(), o.getSlotTime(), o.getService(), o.getProfessionalId());
        }
    }

    /**
     * Resultado da resposta do cliente a uma oferta.
     *
     * @param booked  o agendamento criado ("sim" deu certo) — quem chama confirma e pergunta o pagamento
     * @param message o que responder ao cliente quando nao houve agendamento
     */
    public record Reply(Appointment booked, String message, WaitlistOffer offer) {
        public boolean isBooked() {
            return booked != null;
        }
    }

    public enum Answer { YES, NO }

    // Sem "1"/"2" de proposito: sao as respostas da confirmacao da vespera, que pode estar aberta junto.
    private static final Set<String> YES = Set.of("sim", "s", "ss", "quero", "pode", "pode marcar", "quero sim",
            "sim quero", "reserva", "reservar", "pode reservar", "quero reservar", "bora", "fechado");
    private static final Set<String> NO = Set.of("nao", "n", "nao quero", "nao posso", "nao vou", "nao obrigado",
            "nao da", "passo", "nao vou poder");

    /**
     * "sim"/"quero"/"pode" ou "não" — so respostas curtas e inequivocas; o resto segue a conversa.
     * "quero outro horário" nao e um sim.
     */
    public static Answer parseAnswer(String msg) {
        if (msg == null) return null;
        String t = Normalizer.normalize(msg.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (YES.contains(t) || t.startsWith("sim ")) return Answer.YES;
        if (NO.contains(t)) return Answer.NO;
        return null;
    }

    // ------------------------------------------------------------------ entrar na lista

    /**
     * Coloca (ou atualiza) o cliente na lista de espera. Quem ja esta esperando tem o desejo
     * trocado pelo novo — nao vira duas entradas.
     */
    public WaitlistEntry join(String phone, String name, String preferredDay, String period,
                              String service, String professional) {
        UUID shop = TenantContext.getCurrent();
        WaitlistWish wish = WaitlistWish.parse(preferredDay, WaitlistWish.isAny(period) ? null : period,
                AppointmentDates.today());
        WaitlistEntry entry = waitlistRepository
                .findByBarbershopIdAndCustomerPhoneAndStatus(shop, phone, WaitlistEntry.Status.WAITING)
                .stream().findFirst()
                .orElseGet(() -> WaitlistEntry.builder().barbershopId(shop).customerPhone(phone).build());
        entry.setCustomerName(name);
        String dia = preferredDay == null || preferredDay.isBlank() ? "qualquer dia" : preferredDay.trim();
        entry.setPreferredDay(dia.length() > 50 ? dia.substring(0, 50) : dia);
        entry.setPreferredDate(wish.date());
        entry.setPreferredPeriod(wish.period());
        entry.setService(WaitlistWish.isAny(service) ? null : service.trim());
        entry.setProfessionalId(WaitlistWish.isAny(professional) ? null : professionalIdByName(professional));
        entry.setStatus(WaitlistEntry.Status.WAITING);
        WaitlistEntry saved = waitlistRepository.save(entry);
        log.info("[LISTA ESPERA] {} na lista: dia='{}' data={} periodo={} servico={} barbeiro={}", phone, dia,
                wish.date(), wish.period(), entry.getService(), entry.getProfessionalId());
        return saved != null ? saved : entry;
    }

    private UUID professionalIdByName(String name) {
        String alvo = WaitlistWish.normalize(name);
        try {
            for (Professional p : professionalService.listActive()) {
                if (alvo.equals(WaitlistWish.normalize(p.displayName())) || alvo.equals(WaitlistWish.normalize(p.getName()))
                        || WaitlistWish.normalize(p.getName()).startsWith(alvo + " ")) {
                    return p.getId();
                }
            }
        } catch (Exception e) {
            log.warn("[LISTA ESPERA] Nao consegui resolver o barbeiro '{}': {}", name, e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ vaga liberada

    /**
     * Oferece o horario de um agendamento que acabou de ser cancelado. Chamar com os dados ainda em
     * maos (antes ou depois do delete — so le os campos). Dentro de uma transacao, a oferta so sai
     * depois do commit: se o cancelamento for desfeito, ninguem recebe vaga fantasma.
     *
     * @return true quando alguem recebeu a oferta (ou ela ficou agendada para o commit)
     */
    public boolean offerFreedSlot(Appointment ag) {
        if (ag == null || ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) return false;
        Slot slot = new Slot(ag.getAppointmentDate(), ag.getAppointmentTime(), ag.getService(), ag.getProfessionalId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            UUID shop = TenantContext.getCurrent();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    withTenant(shop, () -> safeOffer(slot));
                }
            });
            return true;
        }
        return safeOffer(slot);
    }

    private boolean safeOffer(Slot slot) {
        try {
            return offerNext(slot, LocalDateTime.now(AppointmentDates.ZONE));
        } catch (Exception e) {
            // Lista de espera nao pode derrubar o cancelamento.
            log.error("[LISTA ESPERA] Erro ao oferecer a vaga {} {}: {}", slot.date(), slot.time(), e.getMessage(), e);
            return false;
        }
    }

    /** Oferece a vaga ao proximo da fila que combina. */
    boolean offerNext(Slot slot, LocalDateTime now) {
        UUID shop = TenantContext.getCurrent();
        AutomationSettings cfg = automationSettingsService.getFor(shop);
        if (!cfg.isWaitlistEnabled()) return false;

        LocalDateTime start = LocalDateTime.of(slot.date(), slot.time());
        if (start.isBefore(now.plusMinutes(MIN_LEAD_MINUTES))) {
            log.info("[LISTA ESPERA] Vaga {} {} ja passou (ou esta em cima da hora) — nao oferecida", slot.date(), slot.time());
            return false;
        }

        List<WaitlistOffer> jaOfertadas = offerRepository
                .findByBarbershopIdAndSlotDateAndSlotTime(shop, slot.date(), slot.time()).stream()
                .filter(o -> Objects.equals(o.getProfessionalId(), slot.professionalId()))
                .toList();
        if (jaOfertadas.stream().anyMatch(o -> o.getStatus() == WaitlistOffer.Status.OFFERED)) {
            return false; // alguem ja esta com a oferta desta vaga na mao
        }
        Set<String> jaReceberam = new HashSet<>();
        Set<UUID> entradasJaOfertadas = new HashSet<>();
        for (WaitlistOffer o : jaOfertadas) {
            jaReceberam.add(o.getCustomerPhone());
            if (o.getEntryId() != null) entradasJaOfertadas.add(o.getEntryId());
        }

        LocalDate today = now.toLocalDate();
        for (WaitlistEntry e : waitlistRepository.findByBarbershopIdAndStatusOrderByCreatedAtAsc(shop, WaitlistEntry.Status.WAITING)) {
            if (entradasJaOfertadas.contains(e.getId()) || jaReceberam.contains(e.getCustomerPhone())) continue;
            if (!matches(e, slot, today, now)) continue;
            if (offerRepository.existsByBarbershopIdAndCustomerPhoneAndStatus(shop, e.getCustomerPhone(), WaitlistOffer.Status.OFFERED)) {
                continue; // ja tem outra vaga na mao: um "sim" precisa ser inequivoco
            }
            String service = e.getService() != null ? e.getService() : slot.service();
            boolean exige = e.getProfessionalId() != null;
            UUID prof = exige ? e.getProfessionalId() : slot.professionalId();
            if (!fits(slot, service, prof, exige)) continue;

            LocalDateTime prazo = now.plusMinutes(cfg.getWaitlistWindowMinutes());
            if (prazo.isAfter(start)) prazo = start;
            WaitlistOffer offer = WaitlistOffer.builder()
                    .barbershopId(shop)
                    .entryId(e.getId())
                    .customerPhone(e.getCustomerPhone())
                    .customerName(e.getCustomerName())
                    .slotDate(slot.date())
                    .slotTime(slot.time())
                    .service(service)
                    .professionalId(prof)
                    .professionalRequired(exige)
                    .status(WaitlistOffer.Status.OFFERED)
                    .expiresAt(prazo)
                    .createdAt(now)
                    .build();
            try {
                offerRepository.save(offer);
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // Corrida com outro cancelamento/expiracao oferecendo a mesma vaga: o outro venceu.
                log.info("[LISTA ESPERA] Vaga {} {} ja oferecida em paralelo", slot.date(), slot.time());
                return false;
            }
            messenger.send(e.getCustomerPhone(), offerMessage(e.getCustomerName(), slot, service,
                    cfg.getWaitlistWindowMinutes(), prazo, now), "SISTEMA");
            log.info("[LISTA ESPERA] Vaga {} {} oferecida a {} (prazo {})", slot.date(), slot.time(), e.getCustomerPhone(), prazo);
            return true;
        }
        log.info("[LISTA ESPERA] Ninguem na lista combina com a vaga {} {}", slot.date(), slot.time());
        return false;
    }

    /**
     * O desejo da entrada combina com a vaga? Data exata (se pediu), senao o dia da semana citado;
     * periodo; barbeiro. Pedido sem data exata vale {@value #STALE_DAYS} dias. O servico nao filtra
     * aqui — e conferido pela agenda ({@link #fits}): o que importa e caber no horario.
     */
    static boolean matches(WaitlistEntry e, Slot slot, LocalDate today, LocalDateTime now) {
        if (e.getStatus() != null && e.getStatus() != WaitlistEntry.Status.WAITING) return false;
        if (e.getPreferredDate() != null) {
            if (e.getPreferredDate().isBefore(today) || !e.getPreferredDate().equals(slot.date())) return false;
        } else {
            if (e.getCreatedAt() != null && e.getCreatedAt().isBefore(now.minusDays(STALE_DAYS))) return false;
            var dia = WaitlistWish.weekdayOf(e.getPreferredDay());
            if (dia.isPresent() && dia.get() != slot.date().getDayOfWeek()) return false;
        }
        if (e.getPreferredPeriod() != null && e.getPreferredPeriod() != WaitlistEntry.Period.of(slot.time())) return false;
        return e.getProfessionalId() == null || slot.professionalId() == null
                || e.getProfessionalId().equals(slot.professionalId());
    }

    /** O servico cabe no horario agora (sem trava — a checagem de verdade e a do agendamento)? */
    private boolean fits(Slot slot, String service, UUID professionalId, boolean exige) {
        try {
            int duracao = availabilityService.duracaoDoServico(service);
            List<UUID> aptos = assignmentService.qualifiedIds(availabilityService.profissionaisAtivos(), service);
            var chk = availabilityService.checarHorario(slot.date(), slot.time(), duracao, professionalId, exige,
                    aptos == null || aptos.isEmpty() ? null : aptos);
            if (chk == null || !chk.livre()) return false;
            return !chk.temProfissionais()
                    || !assignmentService.qualifiedIds(chk.profissionaisLivres(), service).isEmpty();
        } catch (Exception e) {
            log.warn("[LISTA ESPERA] Nao consegui conferir a vaga {} {}: {}", slot.date(), slot.time(), e.getMessage());
            return false;
        }
    }

    static String offerMessage(String name, Slot slot, String service, int windowMinutes, LocalDateTime prazo,
                               LocalDateTime now) {
        String dia = slot.date().getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        dia = dia.substring(0, 1).toUpperCase(PT_BR) + dia.substring(1);
        long minutos = Math.max(1, java.time.Duration.between(now, prazo).toMinutes());
        String servico = service == null || service.isBlank() ? "" : " para *" + service + "*";
        return "Opa, " + AutomationTemplates.firstName(name) + "! 💈\n\n"
                + "Vagou um horário que você estava esperando: *" + dia + "* (" + AppointmentDates.formatDate(slot.date())
                + ") às *" + AppointmentDates.formatTime(slot.time()) + "*" + servico + "! 🎉\n\n"
                + "Quer reservar? Responda *SIM* para eu marcar ou *NÃO* para passar a vez.\n"
                + "_A vaga fica guardada pra você por " + minutos + " min — depois vai para o próximo da lista._";
    }

    // ------------------------------------------------------------------ resposta

    /**
     * Trata a resposta do cliente que tem oferta aberta. Vazio = nao ha oferta ou a mensagem nao
     * e um sim/nao — siga o fluxo normal (a oferta continua valendo ate o prazo).
     */
    public Optional<Reply> handleReply(String phone, String message) {
        UUID shop = TenantContext.getCurrent();
        Optional<WaitlistOffer> aberta = offerRepository
                .findFirstByBarbershopIdAndCustomerPhoneAndStatusOrderByCreatedAtDesc(shop, phone, WaitlistOffer.Status.OFFERED);
        if (aberta.isEmpty()) return Optional.empty();
        Answer answer = parseAnswer(message);
        if (answer == null) return Optional.empty();

        WaitlistOffer offer = aberta.get();
        LocalDateTime now = LocalDateTime.now(AppointmentDates.ZONE);
        String nome = AutomationTemplates.firstName(offer.getCustomerName());
        if (offer.getExpiresAt() != null && !offer.getExpiresAt().isAfter(now)) {
            // Respondeu depois do prazo, antes do scheduler passar: expira aqui mesmo.
            expire(offer, now, false);
            return Optional.of(new Reply(null, "Poxa, " + nome + ", o prazo dessa vaga acabou e ela foi para o próximo da lista 😕\n\n"
                    + "Você continua na lista — se abrir outro horário eu te aviso! 💈", offer));
        }
        offer.setRespondedAt(now);

        if (answer == Answer.NO) {
            offer.setStatus(WaitlistOffer.Status.DECLINED);
            offerRepository.save(offer);
            log.info("[LISTA ESPERA] {} recusou a vaga {} {}", phone, offer.getSlotDate(), offer.getSlotTime());
            safeNext(Slot.of(offer), now);
            return Optional.of(new Reply(null, "Sem problemas, " + nome + "! Você continua na lista de espera e eu te aviso "
                    + "se abrir outro horário. 💈", offer));
        }

        Optional<Appointment> criado;
        try {
            criado = bookingService.agendar(new AgendaBookingService.NovoAgendamento(
                    offer.getCustomerName(), phone, offer.getSlotDate(), offer.getSlotTime(), offer.getService(),
                    null, offer.getProfessionalId(), offer.isProfessionalRequired(), AppointmentSource.BOT));
        } catch (AgendaBookingService.BookingLimitExceededException limite) {
            offer.setStatus(WaitlistOffer.Status.FAILED);
            offerRepository.save(offer);
            safeNext(Slot.of(offer), now);
            return Optional.of(new Reply(null, limite.detail().customerMessage(nome), offer));
        }
        if (criado == null || criado.isEmpty()) {
            offer.setStatus(WaitlistOffer.Status.FAILED);
            offerRepository.save(offer);
            log.info("[LISTA ESPERA] Vaga {} {} ja nao estava livre quando {} aceitou", offer.getSlotDate(), offer.getSlotTime(), phone);
            return Optional.of(new Reply(null, "Poxa, " + nome + ", essa vaga acabou de ser ocupada 😕\n\n"
                    + "Você continua na lista de espera — te aviso se abrir outro horário!", offer));
        }

        Appointment ag = criado.get();
        offer.setStatus(WaitlistOffer.Status.ACCEPTED);
        offer.setAppointmentId(ag.getId());
        offerRepository.save(offer);
        if (offer.getEntryId() != null) {
            waitlistRepository.findById(offer.getEntryId()).ifPresent(e -> {
                e.setStatus(WaitlistEntry.Status.BOOKED);
                waitlistRepository.save(e);
            });
        }
        log.info("[LISTA ESPERA] {} aceitou a vaga {} {} (agendamento {})", phone, offer.getSlotDate(), offer.getSlotTime(), ag.getId());
        return Optional.of(new Reply(ag, null, offer));
    }

    /** O cliente tem uma vaga oferecida esperando resposta? */
    public boolean hasOpenOffer(String phone) {
        return offerRepository.existsByBarbershopIdAndCustomerPhoneAndStatus(
                TenantContext.getCurrent(), phone, WaitlistOffer.Status.OFFERED);
    }

    private void safeNext(Slot slot, LocalDateTime now) {
        try {
            offerNext(slot, now);
        } catch (Exception e) {
            log.error("[LISTA ESPERA] Erro ao passar a vaga {} {} adiante: {}", slot.date(), slot.time(), e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ prazo (scheduler)

    /**
     * Expira as ofertas sem resposta no prazo (todos os tenants) e passa cada vaga ao proximo.
     *
     * @return quantas expiraram
     */
    public int expireDue(LocalDateTime now) {
        List<WaitlistOffer> vencidas = offerRepository.findByStatusAndExpiresAtBefore(WaitlistOffer.Status.OFFERED, now);
        int n = 0;
        for (WaitlistOffer offer : vencidas) {
            try {
                withTenant(offer.getBarbershopId(), () -> {
                    expire(offer, now, true);
                    return true;
                });
                n++;
            } catch (Exception e) {
                log.error("[LISTA ESPERA] Falha ao expirar a oferta {}: {}", offer.getId(), e.getMessage(), e);
            }
        }
        return n;
    }

    private void expire(WaitlistOffer offer, LocalDateTime now, boolean avisar) {
        offer.setStatus(WaitlistOffer.Status.EXPIRED);
        offerRepository.save(offer);
        log.info("[LISTA ESPERA] Oferta da vaga {} {} para {} expirou", offer.getSlotDate(), offer.getSlotTime(), offer.getCustomerPhone());
        if (avisar) {
            messenger.send(offer.getCustomerPhone(), "⏰ O prazo para reservar o horário de *"
                    + AppointmentDates.formatDate(offer.getSlotDate()) + " às " + AppointmentDates.formatTime(offer.getSlotTime())
                    + "* acabou e ele foi para o próximo da lista.\n\nVocê continua na lista de espera — te aviso se abrir outro! 💈",
                    "SISTEMA");
        }
        safeNext(Slot.of(offer), now);
    }

    /** Roda com o tenant informado e devolve o contexto como estava. */
    private static boolean withTenant(UUID shop, java.util.function.BooleanSupplier action) {
        boolean tinha = TenantContext.isSet();
        UUID anterior = tinha ? TenantContext.getCurrent() : null;
        TenantContext.set(shop);
        try {
            return action.getAsBoolean();
        } finally {
            if (anterior != null) TenantContext.set(anterior);
            else TenantContext.clear();
        }
    }
}
