package com.razor.api.service;

import com.razor.api.model.Coupon;
import com.razor.api.util.AppointmentDates;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ponto do cartao fidelidade quando o atendimento <b>de fato aconteceu</b> (comanda fechada).
 *
 * <p>Antes o ponto era dado na hora do AGENDAMENTO e nunca era estornado no cancelamento: dava
 * para marcar e desmarcar dez vezes e ganhar o cupom sem cortar o cabelo — e o
 * {@code lastServiceDate} (base da retencao) ia para "agora" num corte que ainda nao existia.
 *
 * <p>Zerar a cartela e emitir o cupom acontecem na mesma transacao dentro do
 * {@link FidelityService}; o envio do WhatsApp fica fora dela (falha de rede nao desfaz a
 * recompensa) e e sincrono, para a falha de envio aparecer no log com o codigo do cupom.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FidelityVisitService {

    private final FidelityService fidelityService;
    private final WhatsAppService whatsAppService;
    private final BarbershopSettingsService barbershopSettingsService;

    /** Registra o atendimento concluido do cliente. Nunca lanca: fidelidade nao derruba o caixa. */
    public void registerCompletedVisit(String phone) {
        if (phone == null || phone.isBlank()) return;

        Optional<Coupon> recompensa;
        try {
            recompensa = fidelityService.registerVisit(phone);
        } catch (Exception ex) {
            log.error("[FIDELIDADE] Falha ao registrar ponto de {} — pontos preservados pelo rollback", phone, ex);
            return;
        }

        recompensa.ifPresent(coupon -> {
            try {
                String nomeBarbearia = barbershopSettingsService.resolve().displayName();
                String msg = "🎉 *PARABÉNS!* Você completou "
                        + CouponService.FIDELITY_TARGET_POINTS + " pontos no seu Cartão Fidelidade da "
                        + nomeBarbearia + "! 🥳✂️\n\n"
                        + "Como recompensa, você ganhou *"
                        + coupon.getDiscountValue().stripTrailingZeros().toPlainString()
                        + "% de desconto* no seu próximo corte! Apresente o código abaixo no caixa:\n\n"
                        + "🎟️ *" + coupon.getCode() + "*\n\n"
                        + "_Válido até " + AppointmentDates.formatDate(coupon.getValidUntil()) + "._\n\n"
                        + "Obrigado pela preferência e por fazer parte da nossa história! 👊💈";
                whatsAppService.sendTextMessageSync(phone, msg);
            } catch (Exception ex) {
                log.error("[FIDELIDADE] Cupom {} foi emitido para {} mas a mensagem nao saiu — "
                        + "informe o codigo ao cliente manualmente", coupon.getCode(), phone, ex);
            }
        });
    }
}
