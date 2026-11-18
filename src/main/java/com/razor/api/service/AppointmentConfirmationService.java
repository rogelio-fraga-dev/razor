package com.razor.api.service;

import com.razor.api.model.AiImpactEvent;
import com.razor.api.model.Appointment;
import com.razor.api.model.ConfirmationStatus;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.AiImpactEventRepository;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Confirmacao de presenca da vespera (anti-falta, V42).
 *
 * <ol>
 *   <li>{@link #sendRequest}: o scheduler dispara a tarefa {@code CONFIRMATION_REQUEST}; a
 *       pergunta "responde 1 pra confirmar ou 2 pra desmarcar" sai, o agendamento fica
 *       {@code PENDING} e a conversa ganha a chave {@link StateKeys#confirmation} (TTL ate o
 *       horario).</li>
 *   <li>{@link #handleReply}: "1"/"sim"/"confirmo" confirmam; "2"/"nao"/"desmarcar" cancelam
 *       <b>so este</b> horario (mesmo caminho de cancelamento do bot, que cancela so as tarefas
 *       dele) e oferecem a vaga a lista de espera. Qualquer outra coisa segue o fluxo normal (a
 *       chave continua viva).</li>
 *   <li>{@link #handleDeadline}: a tarefa {@code CONFIRMATION_DEADLINE} (padrao 3h antes) marca
 *       {@code NO_RESPONSE} e avisa os admins. Nao cancela sozinho — so se o tenant ligou
 *       {@code confirmation_auto_release}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentConfirmationService {

    private final AppointmentRepository appointmentRepository;
    private final AgendaBookingService bookingService;
    private final WaitlistOfferService waitlistOfferService;
    private final StateStore stateStore;
    private final WhatsAppMessenger messenger;
    private final TenantAdminService tenantAdminService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final AiImpactEventRepository eventRepository;

    /** Estado da conversa: qual agendamento a pergunta da vespera se refere. */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PendingConfirmation {
        private Long appointmentId;
    }

    public enum Answer { CONFIRM, DECLINE }

    private static final Set<String> CONFIRM_EXACT = Set.of(
            "1", "sim", "s", "ss", "confirmo", "confirmado", "confirmada", "confirmar", "confirma", "ok", "okay",
            "certo", "combinado", "beleza", "blz", "fechado", "vou", "vou sim", "sim vou", "sim confirmo",
            "pode confirmar", "confirmadissimo", "estarei ai", "estarei la", "to dentro", "sim sim");
    private static final Set<String> DECLINE_EXACT = Set.of(
            "2", "nao", "n", "desmarcar", "desmarca", "desmarque", "cancelar", "cancela", "cancele",
            "nao vou", "nao posso", "nao vou poder", "nao consigo", "nao vou conseguir", "pode desmarcar",
            "pode cancelar", "desmarcar por favor", "cancelar por favor");

    /**
     * Interpreta a resposta a pergunta da vespera. Reconhece so respostas curtas e inequivocas —
     * "sim, mas posso ir as 16h?" nao e um "sim": vai para o fluxo normal (IA).
     */
    public static Answer parseAnswer(String msg) {
        if (msg == null) return null;
        String t = Normalizer.normalize(msg.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (t.isEmpty()) return null;
        if (CONFIRM_EXACT.contains(t)) return Answer.CONFIRM;
        if (DECLINE_EXACT.contains(t)) return Answer.DECLINE;
        return null;
    }

    // ------------------------------------------------------------------ pergunta (scheduler)

    /**
     * Envia a pergunta da tarefa {@code CONFIRMATION_REQUEST}. Lanca se o WhatsApp recusar (o
     * scheduler marca a tarefa como FAILED). Horario que nao existe mais ou ja tem comparecimento
     * marcado nao e perguntado.
     *
     * @return true quando a pergunta saiu
     */
    public boolean sendRequest(ScheduledTask task) {
        Appointment ag = find(task.getAppointmentId());
        if (ag == null || ag.getAttendanceStatus() != null || ag.getConfirmationStatus() == ConfirmationStatus.CONFIRMED) {
            log.info("[ANTI-FALTA] Confirmacao da tarefa {} ignorada: agendamento ausente ou ja resolvido", task.getId());
            return false;
        }
        messenger.sendSync(task.getCustomerPhone(), task.getMessageContent(), "SISTEMA");
        ag.setConfirmationStatus(ConfirmationStatus.PENDING);
        appointmentRepository.save(ag);
        stateStore.put(StateKeys.confirmation(task.getCustomerPhone()), new PendingConfirmation(ag.getId()), ttlUntil(ag));
        log.info("[ANTI-FALTA] Pergunta de confirmacao enviada para {} (agendamento {})", task.getCustomerPhone(), ag.getId());
        return true;
    }

    /** A chave vive ate o horario: um "1" atrasado (depois do prazo) ainda confirma. */
    private static Duration ttlUntil(Appointment ag) {
        if (ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) return Duration.ofHours(24);
        Duration d = Duration.between(LocalDateTime.now(AppointmentDates.ZONE),
                LocalDateTime.of(ag.getAppointmentDate(), ag.getAppointmentTime()));
        return d.compareTo(Duration.ofMinutes(5)) < 0 ? Duration.ofMinutes(5) : d;
    }

    // ------------------------------------------------------------------ resposta (conversa)

    /**
     * Trata a resposta do cliente se houver pergunta pendente e a mensagem for um "1"/"2" (ou
     * equivalente). Devolve o texto a responder; vazio = nao era resposta, siga o fluxo normal.
     */
    public Optional<String> handleReply(String phone, String message) {
        var pending = stateStore.get(StateKeys.confirmation(phone), PendingConfirmation.class);
        if (pending.isEmpty()) return Optional.empty();
        Answer answer = parseAnswer(message);
        if (answer == null) return Optional.empty();
        stateStore.remove(StateKeys.confirmation(phone));

        Appointment ag = find(pending.get().getAppointmentId());
        if (ag == null) {
            return Optional.of("Esse horário não está mais na agenda 🤔 Se quiser marcar outro, é só me dizer o dia e o serviço. 💈");
        }
        String quando = AppointmentDates.formatDate(ag.getAppointmentDate()) + " às "
                + AppointmentDates.formatTime(ag.getAppointmentTime());
        String nome = firstName(ag);

        if (answer == Answer.CONFIRM) {
            ag.setConfirmationStatus(ConfirmationStatus.CONFIRMED);
            appointmentRepository.save(ag);
            record(AiImpactEvent.Type.CONFIRMED, ag);
            log.info("[ANTI-FALTA] {} confirmou o agendamento {}", phone, ag.getId());
            return Optional.of("Show" + (nome != null ? ", " + nome : "") + "! ✅ Horário confirmado: *" + quando
                    + "*. Te esperamos! 💈");
        }

        record(AiImpactEvent.Type.DECLINED, ag);
        bookingService.cancelarAgendamentoExato(ag);
        waitlistOfferService.offerFreedSlot(ag);
        notifyAdmins("📅 *Horário desmarcado pela confirmação*\n👤 " + displayName(ag) + " (" + phone + ") não vem *"
                + quando + "* (" + ag.getService() + "). A vaga já está livre na agenda.");
        log.info("[ANTI-FALTA] {} desmarcou o agendamento {} pela confirmacao", phone, ag.getId());
        return Optional.of("Tudo bem" + (nome != null ? ", " + nome : "") + "! Desmarquei seu horário de *" + quando
                + "*. Obrigado por avisar 🙏\n\nQuando quiser marcar de novo, é só chamar aqui. 💈");
    }

    // ------------------------------------------------------------------ prazo (scheduler)

    /**
     * Prazo da confirmacao venceu. Sem resposta: marca {@code NO_RESPONSE} e avisa os admins.
     * Com {@code confirmation_auto_release} ligado, tambem cancela e oferece a vaga a lista.
     */
    public void handleDeadline(ScheduledTask task) {
        Appointment ag = find(task.getAppointmentId());
        if (ag == null || ag.getConfirmationStatus() != ConfirmationStatus.PENDING) return;

        ag.setConfirmationStatus(ConfirmationStatus.NO_RESPONSE);
        appointmentRepository.save(ag);
        record(AiImpactEvent.Type.NO_RESPONSE, ag);

        String hora = AppointmentDates.formatTime(ag.getAppointmentTime());
        BarbershopSettingsService.NoShowSettings cfg = barbershopSettingsService.getNoShowSettings();
        if (cfg != null && cfg.autoRelease()) {
            record(AiImpactEvent.Type.AUTO_RELEASED, ag);
            stateStore.remove(StateKeys.confirmation(ag.getCustomerPhone()));
            bookingService.cancelarAgendamentoExato(ag);
            waitlistOfferService.offerFreedSlot(ag);
            messenger.send(ag.getCustomerPhone(), "Oi" + (firstName(ag) != null ? ", " + firstName(ag) : "")
                    + ". Como não recebemos sua confirmação, liberamos o horário das *" + hora + "*. "
                    + "Se ainda quiser vir, responde aqui que a gente procura um horário pra você. 💈", "SISTEMA");
            notifyAdmins("⏰ *Sem confirmação*\n👤 " + displayName(ag) + " não confirmou o horário das *" + hora
                    + "* (" + ag.getService() + "). O horário foi *liberado* e oferecido à lista de espera.");
            log.info("[ANTI-FALTA] Agendamento {} liberado por falta de confirmacao", ag.getId());
        } else {
            notifyAdmins("⏰ *Sem confirmação*\n👤 " + displayName(ag) + " (" + ag.getCustomerPhone()
                    + ") não confirmou o horário das *" + hora + "* (" + ag.getService()
                    + "). O horário continua reservado — vale dar um toque.");
            log.info("[ANTI-FALTA] Agendamento {} sem resposta; admins avisados", ag.getId());
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Registra um fato para o card "O que a IA fez por voce". */
    public void record(AiImpactEvent.Type type, Appointment ag) {
        try {
            eventRepository.save(AiImpactEvent.builder()
                    .barbershopId(TenantContext.getCurrent())
                    .eventType(type)
                    .appointmentId(ag != null ? ag.getId() : null)
                    .customerPhone(ag != null ? ag.getCustomerPhone() : null)
                    .build());
        } catch (Exception e) {
            // Metrica nao pode derrubar o atendimento.
            log.error("[ANTI-FALTA] Falha ao registrar evento {}: {}", type, e.getMessage());
        }
    }

    private Appointment find(Long id) {
        if (id == null) return null;
        return appointmentRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent()).orElse(null);
    }

    private void notifyAdmins(String msg) {
        for (String adm : new java.util.LinkedHashSet<>(tenantAdminService.adminPhones())) {
            messenger.send(adm, msg, "ADMIN");
        }
    }

    private static String firstName(Appointment ag) {
        String n = ag.getCustomerName();
        return n == null || n.isBlank() ? null : n.trim().split("\\s+")[0];
    }

    private static String displayName(Appointment ag) {
        return ag.getCustomerName() == null || ag.getCustomerName().isBlank() ? "Cliente" : ag.getCustomerName();
    }
}
