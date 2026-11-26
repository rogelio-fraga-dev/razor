package com.razor.api.service;

import com.razor.api.model.Coupon;
import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cartao fidelidade: um ponto por atendimento e a recompensa ao completar a cartela.
 *
 * <p><b>Por que isto vive num service proprio e transacional:</b> a versao anterior zerava os
 * pontos e <i>depois</i> tentava emitir o cupom, tudo dentro de um {@code try/catch} que engolia
 * a excecao. Se a emissao falhasse, o cliente perdia os pontos e nao ganhava nada — e ninguem
 * ficava sabendo, porque o erro so virava uma linha de log. Aqui zerar os pontos e emitir o
 * cupom estao na <b>mesma transacao</b>: ou as duas coisas acontecem, ou nenhuma.
 *
 * <p>O envio do WhatsApp fica <b>fora</b> daqui de proposito: e chamada de rede, que falha por
 * motivos que nao deveriam desfazer a recompensa ja conquistada. Quem chama envia depois de a
 * transacao fechar, e o cupom continua valido mesmo se a mensagem nao sair.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FidelityService {

    private final CustomerRepository customerRepository;
    private final CouponService couponService;
    /** Plano da barbearia (piloto sem cartão fidelidade). Opcional: nulo nos testes = liberado. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlanFeatures planFeatures;

    /**
     * Registra a visita do cliente: soma um ponto e, ao completar a cartela, zera e emite o
     * cupom de recompensa.
     *
     * <p><b>Sempre numa transacao NOVA ({@code REQUIRES_NEW}).</b> O chamador real e o
     * fechamento da comanda, que chama aqui no {@code afterCommit}: nesse momento a transacao do
     * fechamento ja foi confirmada, mas os recursos dela ainda estao presos a thread. Com
     * {@code REQUIRED} o metodo "participava" dessa transacao morta — o save era feito num
     * contexto que nunca mais faz flush/commit, e o ponto (e o cupom) se perdiam em silencio.
     *
     * @return o cupom emitido, quando a cartela fechou nesta visita; vazio caso contrario.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Coupon> registerVisit(String phone) {
        Customer customer = customerRepository
                .findByBarbershopIdAndPhoneNumber(TenantContext.getCurrent(), phone)
                .orElse(null);
        if (customer == null) return Optional.empty();

        customer.setLastServiceDate(LocalDateTime.now());
        if (planFeatures != null && !planFeatures.enabled(PlanFeatures.Feature.LOYALTY)) {
            // Plano sem cartão fidelidade (piloto): só registra a visita, sem ponto nem cupom.
            customerRepository.save(customer);
            return Optional.empty();
        }
        int pontos = (customer.getFidelityPoints() == null ? 0 : customer.getFidelityPoints()) + 1;

        if (pontos < CouponService.FIDELITY_TARGET_POINTS) {
            customer.setFidelityPoints(pontos);
            customerRepository.save(customer);
            log.info("[FIDELIDADE] {} tem {} de {} pontos", phone, pontos,
                    CouponService.FIDELITY_TARGET_POINTS);
            return Optional.empty();
        }

        // Cartela fechada. O cupom e emitido na mesma transacao do zeramento: se a emissao
        // falhar, o rollback devolve os pontos ao cliente.
        Coupon coupon = couponService.issueFidelityReward(phone);
        customer.setFidelityPoints(0);
        customerRepository.save(customer);

        log.info("[FIDELIDADE] {} completou a cartela e ganhou o cupom {}", phone, coupon.getCode());
        return Optional.of(coupon);
    }
}
