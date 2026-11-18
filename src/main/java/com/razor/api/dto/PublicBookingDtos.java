package com.razor.api.dto;

import com.razor.api.model.SiteTheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contratos da pagina publica de agendamento online ({@code /b/{slug}/agendar}).
 *
 * <p><b>Fronteira de privacidade, como o {@link PublicSiteResponse}:</b> o que sai daqui e o
 * catalogo, os nomes dos barbeiros e os HORARIOS LIVRES — nunca quem ocupa um horario, telefone
 * de cliente ou de barbeiro, faturamento ou comissao. O id do barbeiro e do servico sai porque o
 * visitante precisa devolve-lo ao escolher; e um UUID opaco, sem nada atras dele que a rota
 * publica exponha.
 */
public final class PublicBookingDtos {

    private PublicBookingDtos() {
    }

    /** Tudo que a pagina precisa para montar o passo a passo. */
    public record Options(
            String slug,
            String barbershopName,
            SiteTheme theme,
            String accentColor,
            String logoUrl,
            String whatsappNumber,
            /** Quantos dias a frente a grade aceita (hoje incluso). */
            int maxDaysAhead,
            /** Dias em que a casa abre, ISO 1=seg..7=dom — a pagina nem oferece os outros. */
            List<Integer> openWeekdays,
            List<ServiceOption> services,
            List<ProfessionalOption> professionals) {
    }

    /** {@code price} nulo quando o dono nao publica precos no site. */
    public record ServiceOption(UUID id, String name, int durationMinutes, BigDecimal price) {
    }

    /**
     * @param serviceIds servicos (ids de {@code services}) que este barbeiro faz — a pagina so o
     *                   oferece para esses (V37). Barbeiro sem restricao lista todos.
     */
    public record ProfessionalOption(UUID id, String name, List<UUID> serviceIds) {
    }

    /** Horarios livres ("HH:mm") de um dia para o servico/barbeiro pedidos. So os horarios. */
    public record Slots(LocalDate date, List<String> slots) {
    }

    /**
     * O pedido do visitante.
     *
     * @param professionalId {@code null} = sem preferencia
     * @param website        honeypot: campo escondido da pagina. Gente nao ve e nao preenche; robo
     *                       que preenche todo input do formulario cai aqui.
     */
    public record Request(
            @NotNull UUID serviceId,
            UUID professionalId,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 5) String time,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 30) String phone,
            @Size(max = 200) String website) {
    }

    /** O que a tela de sucesso mostra. Nada que o visitante ja nao tenha digitado ou escolhido. */
    public record Confirmation(
            String barbershopName,
            String serviceName,
            String professionalName,
            LocalDate date,
            String time) {
    }
}
