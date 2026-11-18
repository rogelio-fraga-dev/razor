package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.Barbershop;
import com.razor.api.model.BarbershopSite;
import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.util.WorkingDays;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Site white-label público")
class PublicSiteServiceTest {

    @Mock private BarbershopRepository barbershopRepository;
    @Mock private BarbershopSiteRepository siteRepository;
    @Mock private ServiceOfferingRepository serviceRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private BarbershopSettingsService settingsService;
    @InjectMocks private PublicSiteService service;

    private final UUID shopId = UUID.randomUUID();

    private Barbershop shop(boolean active) {
        return Barbershop.builder().id(shopId).name("Navalha").slug("navalha").active(active).build();
    }

    private BarbershopSite site(boolean published) {
        BarbershopSite s = BarbershopSite.defaultsFor(shopId);
        s.setPublished(published);
        return s;
    }

    @BeforeEach
    void givenSettings() {
        when(settingsService.resolveFor(any())).thenReturn(
                new BarbershopSettingsService.BotSettings(
                        "Rua X, 100", "", "", null, "",
                        "Navalha de Prata", null, null, null,
                        "09:00", "20:00", WorkingDays.DEFAULT,
                        null, null, null, null, null, null));
        when(serviceRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().name("Corte").price(new BigDecimal("45.00")).durationMinutes(30).build()));
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                Professional.builder().id(UUID.randomUUID()).name("Rafael").phone("5511999").build()));
        when(planRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                SubscriptionPlan.builder().name("Ouro").price(new BigDecimal("119.90")).build()));
    }

    @Test
    @DisplayName("site publicado sai com serviços, equipe e planos")
    void published_returnsContent() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(true)));

        var out = service.bySlug("navalha");

        assertThat(out.barbershopName()).isEqualTo("Navalha de Prata");
        assertThat(out.services()).singleElement()
                .satisfies(s -> assertThat(s.price()).isEqualByComparingTo("45.00"));
        assertThat(out.team()).singleElement().satisfies(t -> assertThat(t.name()).isEqualTo("Rafael"));
        assertThat(out.plans()).hasSize(1);
    }

    @Test
    @DisplayName("site não publicado responde o MESMO 404 de slug inexistente")
    void unpublished_looksLikeNotFound() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(false)));

        // Um erro diferente contaria a estranhos que a barbearia existe e está montando o site.
        assertThatThrownBy(() -> service.bySlug("navalha"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("não encontrado".replace("ã", "a"));
    }

    @Test
    @DisplayName("barbearia inativa não serve site, mesmo publicado")
    void inactiveShop_notFound() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(false)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(true)));

        assertThatThrownBy(() -> service.bySlug("navalha")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("preço só sai quando o dono liga — barbearia de orçamento não quer tabela pública")
    void hidePrices_omitsPrice() {
        BarbershopSite s = site(true);
        s.setShowPrices(false);
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(s));

        var out = service.bySlug("navalha");

        // O serviço continua listado; só o preço some.
        assertThat(out.services()).singleElement().satisfies(sv -> {
            assertThat(sv.name()).isEqualTo("Corte");
            assertThat(sv.price()).isNull();
        });
    }

    @Test
    @DisplayName("equipe e planos desligados somem da resposta, não vão vazios para o template")
    void hiddenSections_areEmpty() {
        BarbershopSite s = site(true);
        s.setShowTeam(false);
        s.setShowPlans(false);
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(s));

        var out = service.bySlug("navalha");

        assertThat(out.team()).isEmpty();
        assertThat(out.plans()).isEmpty();
    }

    @Test
    @DisplayName("nenhum telefone de barbeiro chega ao visitante")
    void team_exposesOnlyName() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(true)));

        var out = service.bySlug("navalha");

        // O record de equipe só tem campos de vitrine (V37: nome, apelido, foto, bio,
        // especialidades, Instagram) — este teste existe para que acrescentar um campo sensível
        // ali passe a quebrar um teste em vez de vazar em silêncio numa página pública. A lista
        // exata está em team_exposesOnlyPublicProfileFields.
        assertThat(PublicSiteResponseFields.teamFieldCount()).isEqualTo(6);
        assertThat(out.team().get(0).name()).isEqualTo("Rafael");
    }

    @Test
    @DisplayName("o horário do site é derivado dos dias estruturados, nunca texto livre")
    void openingHours_derivedFromWorkingDays() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(true)));

        var out = service.bySlug("navalha");

        // O site não pode anunciar um expediente diferente do que a agenda aceita.
        assertThat(out.openingHours()).isEqualTo("Seg a Sáb, das 09:00 às 20:00");
    }

    @Test
    @DisplayName("prévia monta o site mesmo sem publicar — é o rascunho do dono")
    void preview_worksUnpublished() {
        when(barbershopRepository.findById(shopId)).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(false)));

        var out = service.preview(shopId);

        assertThat(out.slug()).isEqualTo("navalha");
    }

    @Test
    @DisplayName("barbearia sem site ainda tem prévia, com os padrões do sistema")
    void preview_withoutRow_usesDefaults() {
        when(barbershopRepository.findById(shopId)).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.empty());

        var out = service.preview(shopId);

        assertThat(out.template()).isEqualTo(com.razor.api.model.SiteTemplate.CLASSICO);
        assertThat(out.theme()).isEqualTo(com.razor.api.model.SiteTheme.AUTO);
    }

    @Test
    @DisplayName("prévia completa ignora os interruptores salvos (o editor aplica os do rascunho)")
    void preview_complete_ignoresToggles() {
        BarbershopSite s = site(false);
        s.setShowPrices(false);
        s.setShowTeam(false);
        s.setShowPlans(false);
        when(barbershopRepository.findById(shopId)).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(s));

        var normal = service.preview(shopId);
        assertThat(normal.services().get(0).price()).isNull();
        assertThat(normal.team()).isEmpty();
        assertThat(normal.plans()).isEmpty();

        var completo = service.preview(shopId, true);
        assertThat(completo.services().get(0).price()).isEqualByComparingTo("45.00");
        assertThat(completo.team()).hasSize(1);
        assertThat(completo.plans()).hasSize(1);
    }

    @Test
    @DisplayName("conteúdo por seção sai no site público; JSON inválido no banco vira padrão")
    void content_isExposed() {
        BarbershopSite s = site(true);
        s.setContentJson("{\"sections\":{\"cta\":{\"title\":\"Vem cortar\"}},\"motion\":\"off\"}");
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(s));

        var out = service.bySlug("navalha");
        assertThat(out.content().sections().get("cta").title()).isEqualTo("Vem cortar");
        assertThat(out.content().motion()).isEqualTo("off");

        s.setContentJson("{\"sections\":{\"cta\":{\"onclick\":\"x\"}}}");
        assertThat(service.bySlug("navalha").content()).isNull();
    }

    /** Reflexão só para travar o formato do DTO público — ver o teste de equipe. */
    static final class PublicSiteResponseFields {
        static int teamFieldCount() {
            return com.razor.api.dto.PublicSiteResponse.TeamMember.class.getRecordComponents().length;
        }
    }

    @Test
    @DisplayName("equipe sai com foto, apelido, bio, especialidades e Instagram — e nada interno")
    void team_exposesOnlyPublicProfileFields() {
        when(barbershopRepository.findBySlug("navalha")).thenReturn(Optional.of(shop(true)));
        when(siteRepository.findById(shopId)).thenReturn(Optional.of(site(true)));
        Professional diego = Professional.builder().id(UUID.randomUUID()).name("Diego Santos")
                .phone("5511988887777").commissionPercent(new BigDecimal("40"))
                .nickname("Diego").photoData("data:image/jpeg;base64,/9j/AA==").bio("10 anos de navalha")
                .specialties("Degradê\nBarba").instagramUrl("https://instagram.com/diego").build();
        Professional inativo = Professional.builder().id(UUID.randomUUID()).name("Saiu").active(false).build();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(diego, inativo));

        var out = service.bySlug("navalha");

        assertThat(out.team()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("Diego Santos");
            assertThat(t.nickname()).isEqualTo("Diego");
            assertThat(t.photoUrl()).startsWith("data:image/jpeg;base64,");
            assertThat(t.bio()).isEqualTo("10 anos de navalha");
            assertThat(t.specialties()).containsExactly("Degradê", "Barba");
            assertThat(t.instagramUrl()).isEqualTo("https://instagram.com/diego");
        });
        // A fronteira e o proprio record: nenhum componente para telefone, comissao ou id.
        assertThat(java.util.Arrays.stream(com.razor.api.dto.PublicSiteResponse.TeamMember.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("name", "nickname", "photoUrl", "bio", "specialties", "instagramUrl");
    }
}
