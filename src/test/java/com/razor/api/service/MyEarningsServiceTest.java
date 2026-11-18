package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.CommissionService.ProfessionalCommission;
import com.razor.api.service.MyEarningsService.Earnings;
import com.razor.api.service.MyEarningsService.Period;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Autorizacao do "quanto eu ganhei": cada barbeiro ve so o proprio dinheiro. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyEarningsServiceTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private CommissionService commissionService;
    @InjectMocks private MyEarningsService service;

    private final UUID shop = UUID.randomUUID();
    private final UUID otherShop = UUID.randomUUID();
    private final UUID rafael = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID barberUserId = UUID.randomUUID();
    private final UUID ownerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantUserRepository.findById(barberUserId)).thenReturn(Optional.of(TenantUser.builder()
                .id(barberUserId).barbershopId(shop).email("b@x").role(UserRole.BARBER)
                .professionalId(rafael).build()));
        when(tenantUserRepository.findById(ownerUserId)).thenReturn(Optional.of(TenantUser.builder()
                .id(ownerUserId).barbershopId(shop).email("o@x").role(UserRole.OWNER).build()));
        when(professionalRepository.findById(rafael)).thenReturn(Optional.of(
                Professional.builder().id(rafael).barbershopId(shop).name("Rafael").build()));
        when(professionalRepository.findById(bruno)).thenReturn(Optional.of(
                Professional.builder().id(bruno).barbershopId(shop).name("Bruno").build()));
        when(commissionService.commissionsForPeriod(any(), any())).thenReturn(List.of(
                new ProfessionalCommission(rafael, "Rafael", new BigDecimal("40"), 3,
                        new BigDecimal("150.00"), BigDecimal.ZERO, 0, BigDecimal.ZERO, new BigDecimal("60.00")),
                new ProfessionalCommission(bruno, "Bruno", new BigDecimal("45"), 5,
                        new BigDecimal("300.00"), BigDecimal.ZERO, 0, BigDecimal.ZERO, new BigDecimal("135.00"))));
    }

    private TenantUserPrincipal barber() {
        return new TenantUserPrincipal(barberUserId, "b@x", shop, UserRole.BARBER);
    }

    @Test
    @DisplayName("barbeiro ve só a propria comissão, mesmo com colegas no período")
    void barber_seesOnlyOwn() {
        Earnings e = service.earnings(barber(), "today", null);

        assertThat(e.linked()).isTrue();
        assertThat(e.professionalId()).isEqualTo(rafael);
        assertThat(e.atendimentos()).isEqualTo(3);
        assertThat(e.comissao()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("barbeiro pedindo o professionalId de um colega leva 403 e nada é apurado")
    void barber_cannotSeeOthers() {
        assertThatThrownBy(() -> service.earnings(barber(), "month", bruno))
                .isInstanceOf(ForbiddenException.class);
        verify(commissionService, never()).commissionsForPeriod(any(), any());
    }

    @Test
    @DisplayName("recepção também não consulta barbeiro alheio")
    void reception_cannotSeeOthers() {
        UUID recId = UUID.randomUUID();
        when(tenantUserRepository.findById(recId)).thenReturn(Optional.of(TenantUser.builder()
                .id(recId).barbershopId(shop).email("r@x").role(UserRole.RECEPTION).build()));
        TenantUserPrincipal rec = new TenantUserPrincipal(recId, "r@x", shop, UserRole.RECEPTION);

        assertThatThrownBy(() -> service.earnings(rec, "today", rafael)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("barbeiro pedindo o próprio id explicitamente é aceito")
    void barber_explicitOwnId() {
        assertThat(service.earnings(barber(), "week", rafael).comissao()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("barbeiro sem vínculo recebe linked=false e zero, sem apurar ninguém")
    void unlinked() {
        UUID id = UUID.randomUUID();
        when(tenantUserRepository.findById(id)).thenReturn(Optional.of(TenantUser.builder()
                .id(id).barbershopId(shop).email("n@x").role(UserRole.BARBER).build()));

        Earnings e = service.earnings(new TenantUserPrincipal(id, "n@x", shop, UserRole.BARBER), null, null);

        assertThat(e.linked()).isFalse();
        assertThat(e.comissao()).isEqualByComparingTo("0");
        verify(commissionService, never()).commissionsForPeriod(any(), any());
    }

    @Test
    @DisplayName("dono pode consultar qualquer barbeiro do próprio tenant")
    void owner_canSeeAnyInShop() {
        TenantUserPrincipal owner = new TenantUserPrincipal(ownerUserId, "o@x", shop, UserRole.OWNER);
        assertThat(service.earnings(owner, "fortnight", bruno).comissao()).isEqualByComparingTo("135.00");
    }

    @Test
    @DisplayName("dono não alcança profissional de outra barbearia (404)")
    void owner_cannotSeeOtherTenant() {
        UUID alien = UUID.randomUUID();
        when(professionalRepository.findById(alien)).thenReturn(Optional.of(
                Professional.builder().id(alien).barbershopId(otherShop).name("X").build()));
        TenantUserPrincipal owner = new TenantUserPrincipal(ownerUserId, "o@x", shop, UserRole.OWNER);

        assertThatThrownBy(() -> service.earnings(owner, "today", alien)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("token de um tenant com usuário de outro tenant não passa")
    void userFromOtherTenant() {
        TenantUserPrincipal forged = new TenantUserPrincipal(barberUserId, "b@x", otherShop, UserRole.BARBER);
        assertThatThrownBy(() -> service.earnings(forged, "today", null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("quinzena: 1–15 e 16–hoje; período inválido vira 400")
    void periods() {
        LocalDate d10 = LocalDate.of(2026, 11, 10);
        LocalDate d20 = LocalDate.of(2026, 11, 20);
        assertThat(Period.parse("quinzena").range(d10)).containsExactly(LocalDate.of(2026, 11, 1), d10);
        assertThat(Period.FORTNIGHT.range(d20)).containsExactly(LocalDate.of(2026, 11, 16), d20);
        assertThat(Period.WEEK.range(LocalDate.of(2026, 11, 18))).containsExactly(LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 18));
        assertThatThrownBy(() -> Period.parse("ano")).isInstanceOf(IllegalArgumentException.class);
    }
}
