package com.razor.api.service;

import com.razor.api.model.Professional;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.CommissionService.ProfessionalCommission;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "Quanto eu ganhei" (V44): a comissao do PROPRIO profissional do usuario logado.
 *
 * <p>A regra de quem ve o que mora aqui, e nao no front: o barbeiro so enxerga o profissional
 * ligado ao proprio login. Pedir outro {@code professionalId} e recusado com 403 para todo papel
 * que nao seja o dono — o dono pode consultar qualquer barbeiro do proprio tenant (ele ja ve
 * todos na tela de Comissoes).
 */
@Service
@RequiredArgsConstructor
public class MyEarningsService {

    private final TenantUserRepository tenantUserRepository;
    private final ProfessionalRepository professionalRepository;
    private final CommissionService commissionService;

    /** Periodos de 1 toque do "Meu dia". A quinzena e 1–15 ou 16–fim do mes (como o acerto). */
    public enum Period {
        TODAY, YESTERDAY, WEEK, FORTNIGHT, MONTH;

        public static Period parse(String raw) {
            if (raw == null || raw.isBlank()) return TODAY;
            String v = raw.trim().toUpperCase(Locale.ROOT);
            if (v.equals("QUINZENA")) return FORTNIGHT;
            try {
                return Period.valueOf(v);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Período inválido: use today, yesterday, week, fortnight ou month.");
            }
        }

        /** [inicio, fim] do periodo em relacao a {@code today}. */
        public LocalDate[] range(LocalDate today) {
            return switch (this) {
                case TODAY -> new LocalDate[] {today, today};
                case YESTERDAY -> new LocalDate[] {today.minusDays(1), today.minusDays(1)};
                case WEEK -> new LocalDate[] {today.with(DayOfWeek.MONDAY), today};
                case FORTNIGHT -> today.getDayOfMonth() <= 15
                        ? new LocalDate[] {today.withDayOfMonth(1), today}
                        : new LocalDate[] {today.withDayOfMonth(16), today};
                case MONTH -> new LocalDate[] {today.withDayOfMonth(1), today};
            };
        }
    }

    public record Earnings(
            boolean linked,
            UUID professionalId,
            String professionalName,
            String period,
            LocalDate from,
            LocalDate to,
            int atendimentos,
            BigDecimal faturamento,
            int produtos,
            BigDecimal faturamentoProdutos,
            BigDecimal comissao) {

        static Earnings unlinked(Period p, LocalDate[] r) {
            return new Earnings(false, null, null, p.name(), r[0], r[1], 0,
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public Earnings earnings(TenantUserPrincipal principal, String rawPeriod, UUID requestedProfessionalId) {
        Period period = Period.parse(rawPeriod);
        LocalDate[] range = period.range(AppointmentDates.today());

        TenantUser user = tenantUserRepository.findById(principal.userId())
                .filter(u -> Objects.equals(u.getBarbershopId(), principal.barbershopId()))
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        UUID target = user.getProfessionalId();
        if (requestedProfessionalId != null && !requestedProfessionalId.equals(target)) {
            // So o dono consulta outro barbeiro. Barbeiro e recepcao: so o proprio vinculo.
            if (principal.role() != UserRole.OWNER) {
                throw new ForbiddenException("Você só pode ver os seus próprios ganhos.");
            }
            target = requestedProfessionalId;
        }
        if (target == null) return Earnings.unlinked(period, range);

        UUID shop = principal.barbershopId();
        final UUID wanted = target;
        Professional prof = professionalRepository.findById(wanted)
                .filter(p -> Objects.equals(p.getBarbershopId(), shop))
                .orElseThrow(() -> new NotFoundException("Profissional não encontrado."));

        ProfessionalCommission mine = commissionService.commissionsForPeriod(range[0], range[1]).stream()
                .filter(c -> wanted.equals(c.professionalId()))
                .findFirst()
                .orElse(null);

        if (mine == null) {
            return new Earnings(true, prof.getId(), prof.getName(), period.name(), range[0], range[1],
                    0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new Earnings(true, prof.getId(), prof.getName(), period.name(), range[0], range[1],
                mine.atendimentos(), mine.faturamento(), mine.produtos(), mine.faturamentoProdutos(),
                mine.comissao());
    }
}
