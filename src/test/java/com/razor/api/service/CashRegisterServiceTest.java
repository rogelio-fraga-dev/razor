package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.CashSessionSummary;
import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.CashRegisterSession;
import com.razor.api.repository.CashMovementRepository;
import com.razor.api.repository.CashRegisterSessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link CashRegisterService}: invariantes (um caixa aberto por vez, venda exige forma
 * de pagamento) e a matemática da gaveta (fundo + suprimentos − sangrias + vendas em dinheiro).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashRegisterServiceTest {

    @Mock private CashRegisterSessionRepository sessionRepository;
    @Mock private CashMovementRepository movementRepository;
    @InjectMocks private CashRegisterService service;

    private CashRegisterSession openSession(BigDecimal opening) {
        return CashRegisterSession.builder()
                .id(UUID.randomUUID())
                .openingAmount(opening.setScale(2))
                .status(CashRegisterSession.Status.OPEN)
                .build();
    }

    private CashMovement venda(BigDecimal amount, CashPaymentMethod method) {
        return CashMovement.builder().type(CashMovementType.VENDA).paymentMethod(method).amount(amount).build();
    }

    private CashMovement movimento(CashMovementType type, BigDecimal amount) {
        return CashMovement.builder().type(type).amount(amount).build();
    }

    @Test
    @DisplayName("abre caixa quando nenhum está aberto")
    void open_whenNoneOpen() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        CashSessionSummary out = service.open(new BigDecimal("100"), UUID.randomUUID(), "Chefe");

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.openingAmount()).isEqualByComparingTo("100.00");
        assertThat(out.expectedDrawer()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("recusa abrir quando já há um caixa aberto")
    void open_whenAlreadyOpen_throws() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any()))
                .thenReturn(Optional.of(openSession(new BigDecimal("50"))));

        assertThatThrownBy(() -> service.open(new BigDecimal("100"), UUID.randomUUID(), "Chefe"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("recusa fundo de troco negativo")
    void open_negative_throws() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(new BigDecimal("-1"), UUID.randomUUID(), "Chefe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("movimento sem caixa aberto é recusado")
    void movement_noOpen_throws() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMovement(
                CashMovementType.SANGRIA, null, new BigDecimal("10"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("venda sem forma de pagamento é recusada")
    void movement_saleWithoutMethod_throws() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any()))
                .thenReturn(Optional.of(openSession(new BigDecimal("100"))));

        assertThatThrownBy(() -> service.addMovement(
                CashMovementType.VENDA, null, new BigDecimal("40"), "Corte"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("valor não positivo é recusado")
    void movement_nonPositive_throws() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any()))
                .thenReturn(Optional.of(openSession(new BigDecimal("100"))));

        assertThatThrownBy(() -> service.addMovement(
                CashMovementType.SUPRIMENTO, null, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("gaveta esperada = fundo + suprimentos − sangrias + vendas em dinheiro")
    void expectedDrawer_math() {
        CashRegisterSession session = openSession(new BigDecimal("100"));
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.of(session));
        when(movementRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of(
                venda(new BigDecimal("50"), CashPaymentMethod.DINHEIRO),
                venda(new BigDecimal("30"), CashPaymentMethod.PIX),
                movimento(CashMovementType.SANGRIA, new BigDecimal("20")),
                movimento(CashMovementType.SUPRIMENTO, new BigDecimal("10"))));

        CashSessionSummary out = service.currentSummary();

        // 100 + 10 - 20 + 50 = 140 (PIX não entra na gaveta)
        assertThat(out.expectedDrawer()).isEqualByComparingTo("140.00");
        assertThat(out.totals().vendasDinheiro()).isEqualByComparingTo("50.00");
        assertThat(out.totals().vendasPix()).isEqualByComparingTo("30.00");
        assertThat(out.totals().vendasTotal()).isEqualByComparingTo("80.00");
        assertThat(out.totals().sangrias()).isEqualByComparingTo("20.00");
        assertThat(out.totals().suprimentos()).isEqualByComparingTo("10.00");
        assertThat(out.difference()).isNull(); // ainda aberto
    }

    @Test
    @DisplayName("fechamento calcula a diferença (contado − esperado) e marca CLOSED")
    void close_computesDifference() {
        CashRegisterSession session = openSession(new BigDecimal("100"));
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.of(session));
        when(movementRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of(
                venda(new BigDecimal("50"), CashPaymentMethod.DINHEIRO),
                movimento(CashMovementType.SUPRIMENTO, new BigDecimal("10"))));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // esperado = 100 + 10 + 50 = 160; contado 165 → sobra de 5
        CashSessionSummary out = service.close(new BigDecimal("165"), "conferido", UUID.randomUUID(), "Chefe");

        assertThat(out.status()).isEqualTo("CLOSED");
        assertThat(out.closingExpected()).isEqualByComparingTo("160.00");
        assertThat(out.closingCounted()).isEqualByComparingTo("165.00");
        assertThat(out.difference()).isEqualByComparingTo("5.00");
        assertThat(session.getStatus()).isEqualTo(CashRegisterSession.Status.CLOSED);
    }

    @Test
    @DisplayName("currentSummary é nulo quando o caixa está fechado")
    void currentSummary_noOpen_null() {
        when(sessionRepository.findByBarbershopIdAndStatus(any(), any())).thenReturn(Optional.empty());
        assertThat(service.currentSummary()).isNull();
    }
}
