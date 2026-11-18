package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.WaitlistEntry;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Oferta de vaga liberada para a lista de espera. Extraido do {@link CustomerConversationService}
 * (V42) porque agora a vaga tambem e liberada fora da conversa do cliente que desmarcou: pela
 * resposta "2" da confirmacao da vespera e pela liberacao automatica de quem nao respondeu.
 *
 * <p>So oferece: o "sim" do cliente da lista e tratado na conversa dele
 * ({@link StateKeys#waitlistOffer}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistOfferService {

    /** A vaga oferecida deixa de valer no dia seguinte. */
    static final Duration WAITLIST_TTL = Duration.ofHours(24);

    private final WaitlistRepository waitlistRepository;
    private final StateStore stateStore;
    private final WhatsAppMessenger messenger;

    // Publica + construtor vazio: serializada em JSON pelo StateStore.
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class WaitlistOffer {
        private String phone;
        private String name;
        private String date;
        private String time;
        private String service;
    }

    /**
     * Oferece o horario de um agendamento que acabou de ser cancelado. Chamar com os dados ainda
     * em maos (antes ou depois do delete, tanto faz — so le os campos).
     *
     * @return true quando alguem da lista recebeu a oferta
     */
    public boolean offerFreedSlot(Appointment ag) {
        if (ag == null || ag.getAppointmentDate() == null) return false;
        String diaSemana = ag.getAppointmentDate().getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
        return offer(diaSemana, AppointmentDates.formatDate(ag.getAppointmentDate()),
                AppointmentDates.formatTime(ag.getAppointmentTime()), ag.getService());
    }

    /** Oferece a vaga ao primeiro da lista cujo dia preferido casa com o dia da semana. */
    public boolean offer(String diaSemana, String date, String time, String service) {
        try {
            List<WaitlistEntry> allEntries = waitlistRepository.findByBarbershopId(TenantContext.getCurrent());
            WaitlistEntry matched = null;

            String cleanedDia = normalize(diaSemana);
            for (WaitlistEntry entry : allEntries) {
                if (entry.getPreferredDay() == null) continue;
                String cleanedPref = normalize(entry.getPreferredDay());
                if (cleanedPref.contains(cleanedDia) || cleanedDia.contains(cleanedPref)) {
                    matched = entry;
                    break;
                }
            }
            if (matched == null) return false;

            WaitlistOffer offer = new WaitlistOffer(matched.getCustomerPhone(), matched.getCustomerName(), date, time, service);
            stateStore.put(StateKeys.waitlistOffer(matched.getCustomerPhone()), offer, WAITLIST_TTL);

            String capitalizeDia = diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1);
            String first = matched.getCustomerName() != null ? matched.getCustomerName().split(" ")[0] : "tudo bem";
            String notificationMsg = "Opa, " + first + "! 💈\n\n" +
                    "Um horário acaba de vagar para este *" + capitalizeDia + "* (" + date + ") às *" + time + "* para *" + service + "*! 🎉\n\n" +
                    "Deseja reservar este horário para você?\n\n" +
                    "Responda apenas **SIM** para confirmar o agendamento automático!";

            waitlistRepository.delete(matched);
            messenger.send(matched.getCustomerPhone(), notificationMsg, "SISTEMA");
            log.info("[LISTA ESPERA] Oferta enviada para {} para o dia {} às {}", matched.getCustomerPhone(), date, time);
            return true;
        } catch (Exception e) {
            log.error("[LISTA ESPERA] Erro ao verificar lista de espera: {}", e.getMessage(), e);
            return false;
        }
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9]", "");
    }
}
