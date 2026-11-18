package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.DepositStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sinal para quem falta muito (V42). Com {@code no_show_deposit_after = N}, o cliente com N ou
 * mais faltas ({@code attendance_status = NO_SHOW}) nos ultimos {@value #WINDOW_DAYS} dias que
 * marca <b>pelo bot</b> recebe o PIX do sinal, e o agendamento fica com
 * {@code deposit_status = PENDING} ate o dono confirmar no painel.
 *
 * <p>Limites (de proposito, para ficar simples): nao ha conferencia automatica do pagamento (o
 * dono confirma); o horario nao e cancelado se o sinal nao vier; o sinal nao e abatido
 * automaticamente da comanda; site e painel nao exigem sinal. Sem PIX configurado a regra nao
 * vale — nunca inventamos chave.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowDepositService {

    public static final int WINDOW_DAYS = 180;

    private final AppointmentRepository appointmentRepository;
    private final BarbershopSettingsService barbershopSettingsService;
    private final PricingService pricingService;

    /** Faltas do cliente na janela. */
    public long recentNoShows(String phone) {
        return appointmentRepository.countByBarbershopIdAndCustomerPhoneAndAttendanceStatusAndAppointmentDateGreaterThanEqual(
                TenantContext.getCurrent(), phone, AttendanceStatus.NO_SHOW,
                AppointmentDates.today().minusDays(WINDOW_DAYS));
    }

    /**
     * Se a regra vale para o cliente deste agendamento recem-criado, marca o sinal como pendente
     * e devolve a mensagem com o PIX para enviar no lugar da pergunta de pagamento.
     */
    public Optional<String> requireDepositIfNeeded(Appointment ag, String firstName) {
        if (ag == null || ag.getCustomerPhone() == null) return Optional.empty();
        BarbershopSettingsService.NoShowSettings cfg = barbershopSettingsService.getNoShowSettings();
        if (cfg == null || cfg.depositAfter() == null) return Optional.empty();
        BarbershopSettingsService.BotSettings bot = barbershopSettingsService.resolve();
        if (bot == null || !bot.hasPix()) return Optional.empty();

        long faltas = recentNoShows(ag.getCustomerPhone());
        if (faltas < cfg.depositAfter()) return Optional.empty();

        BigDecimal valor = cfg.depositAmount() != null ? cfg.depositAmount() : pricingService.priceFor(ag.getService());
        ag.setDepositStatus(DepositStatus.PENDING);
        appointmentRepository.save(ag);
        log.info("[ANTI-FALTA] Sinal exigido de {} ({} falta(s) em {} dias) no agendamento {}",
                ag.getCustomerPhone(), faltas, WINDOW_DAYS, ag.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("Seu horário de *").append(AppointmentDates.formatDate(ag.getAppointmentDate()))
                .append("* às *").append(AppointmentDates.formatTime(ag.getAppointmentTime()))
                .append("* (").append(ag.getService()).append(") está *pré-reservado*. 📌\n\n");
        sb.append(firstName != null ? firstName + ", c" : "C")
                .append("omo tivemos algumas faltas nos últimos meses, pra garantir o horário pedimos um *sinal");
        if (valor != null && valor.signum() > 0) {
            sb.append(" de ").append(String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", valor));
        }
        sb.append("* via PIX:\n\n🔑 *CHAVE PIX:* `").append(bot.pixKey()).append("`\n");
        if (bot.pixCopyPaste() != null && !bot.pixCopyPaste().isBlank()) {
            sb.append("\n📌 *PIX Copia e Cola:*\n`").append(bot.pixCopyPaste()).append("`\n");
        }
        sb.append("\nManda o comprovante aqui mesmo que a barbearia confirma. 💈");
        return Optional.of(sb.toString());
    }
}
