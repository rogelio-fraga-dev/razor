package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conteudo do site white-label da barbearia (V33). Mapeia {@code barbershop_site}.
 *
 * <p>O <b>codigo</b> do template e nosso e centralizado; aqui vive so o conteudo. Trocar de
 * template nao perde nada porque os tres consomem os mesmos campos — cada um os apresenta do seu
 * jeito. Se cada template tivesse o proprio formato de conteudo, mudar de ideia custaria
 * redigitar o site.
 */
@Entity
@Table(name = "barbershop_site")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarbershopSite {

    /** Um site por barbearia — a propria chave estrangeira e a primaria. */
    @Id
    @Column(name = "barbershop_id")
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_id", nullable = false, length = 20)
    private SiteTemplate templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private SiteTheme themeMode;

    /** Unica cor que o dono escolhe. O resto da paleta o template deriva. */
    @Column(name = "accent_color", nullable = false, length = 9)
    private String accentColor;

    @Column(length = 120)
    private String headline;

    @Column(length = 255)
    private String subheadline;

    @Column(name = "about_text", columnDefinition = "text")
    private String aboutText;

    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;

    @Column(name = "hero_image_url", columnDefinition = "text")
    private String heroImageUrl;

    /** URLs separadas por quebra de linha — ver {@link #galleryList()}. */
    @Column(name = "gallery_urls", columnDefinition = "text")
    private String galleryUrls;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(name = "instagram_url", columnDefinition = "text")
    private String instagramUrl;

    @Column(name = "maps_url", columnDefinition = "text")
    private String mapsUrl;

    /**
     * Textos e visibilidade por secao, texto do botao e animacoes (V45), em JSON. So entra aqui
     * o que passou pelo {@code SiteContentPolicy}; nulo = tudo no padrao do template.
     */
    @Column(name = "content_json", columnDefinition = "text")
    private String contentJson;

    @Column(name = "show_team", nullable = false)
    private boolean showTeam;

    @Column(name = "show_plans", nullable = false)
    private boolean showPlans;

    @Column(name = "show_prices", nullable = false)
    private boolean showPrices;

    /** Pagina publica de agendamento online ligada (V38). Desligada, o site so oferece o WhatsApp. */
    @Column(name = "online_booking_enabled", nullable = false)
    private boolean onlineBookingEnabled;

    /** Rascunho ate o dono publicar: site nao publicado responde 404 no endereco publico. */
    @Column(nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** A galeria como lista, ignorando linhas em branco. */
    public List<String> galleryList() {
        if (galleryUrls == null || galleryUrls.isBlank()) return List.of();
        return galleryUrls.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Rascunho com os padroes do sistema, para a barbearia que ainda nao mexeu no site. */
    public static BarbershopSite defaultsFor(UUID barbershopId) {
        return BarbershopSite.builder()
                .barbershopId(barbershopId)
                .templateId(SiteTemplate.CLASSICO)
                .themeMode(SiteTheme.AUTO)
                .accentColor("#3E63FF")
                .showTeam(true)
                .showPlans(true)
                .showPrices(true)
                .published(false)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
