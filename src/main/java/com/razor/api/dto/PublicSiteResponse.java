package com.razor.api.dto;

import com.razor.api.model.SiteTemplate;
import com.razor.api.model.SiteTheme;
import java.math.BigDecimal;
import java.util.List;

/**
 * O site white-label como o VISITANTE o recebe. Endpoint publico, sem autenticacao.
 *
 * <p><b>Este DTO e a fronteira de privacidade do tenant.</b> Ele existe separado dos DTOs do
 * painel justamente para que nenhum campo interno vaze por descuido: aqui nao entram id de
 * cliente, telefone de cliente, faturamento, comissao, custo, nem o id do profissional. O que
 * sai e o que a barbearia ja exporia numa vitrine de rua — nome, servico, preco, equipe.
 *
 * <p>Preco so aparece quando o dono liga {@code showPrices}: barbearia que trabalha com
 * orcamento nao quer tabela publica, e forcar isso seria decidir o negocio dela.
 */
public record PublicSiteResponse(
        String slug,
        String barbershopName,
        SiteTemplate template,
        SiteTheme theme,
        String accentColor,

        String headline,
        String subheadline,
        String aboutText,
        String logoUrl,
        String heroImageUrl,
        List<String> gallery,

        String whatsappNumber,
        String instagramUrl,
        String mapsUrl,
        String addressText,
        /** Frase de funcionamento derivada dos dias estruturados (V32) — nunca texto livre solto. */
        String openingHours,

        List<Service> services,
        List<TeamMember> team,
        List<Plan> plans) {

    /** {@code price} nulo quando o dono optou por nao publicar precos. */
    public record Service(String name, BigDecimal price, Integer durationMinutes) {
    }

    /** So o nome. Telefone e comissao do barbeiro nao tem por que existir numa pagina publica. */
    public record TeamMember(String name) {
    }

    public record Plan(String name, BigDecimal price, String description) {
    }
}
