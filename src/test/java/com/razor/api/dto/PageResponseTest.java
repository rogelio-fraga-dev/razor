package com.razor.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@DisplayName("Envelope de paginação")
class PageResponseTest {

    private static final Sort BY_NAME = Sort.by("name");

    @Test
    @DisplayName("size acima do teto é limitado — senão ?size=999999 anularia a paginação")
    void pageable_clampsSizeToMax() {
        Pageable p = PageResponse.pageable(0, 999_999, BY_NAME);

        assertThat(p.getPageSize()).isEqualTo(PageResponse.MAX_SIZE);
    }

    @Test
    @DisplayName("pedir tamanho demais não dá erro, só entrega o máximo")
    void pageable_overMax_doesNotFail() {
        // Recusar a requisição só atrapalharia quem está integrando; limitar resolve.
        assertThat(PageResponse.pageable(3, 500, BY_NAME).getPageNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("valores ausentes ou inválidos caem no padrão em vez de estourar")
    void pageable_tolerantDefaults() {
        assertThat(PageResponse.pageable(null, null, BY_NAME).getPageSize())
                .isEqualTo(PageResponse.DEFAULT_SIZE);
        assertThat(PageResponse.pageable(null, null, BY_NAME).getPageNumber()).isZero();
        // Página negativa vira a primeira; tamanho zero vira o padrão.
        assertThat(PageResponse.pageable(-5, 0, BY_NAME).getPageNumber()).isZero();
        assertThat(PageResponse.pageable(-5, 0, BY_NAME).getPageSize())
                .isEqualTo(PageResponse.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("a ordenação pedida chega ao Pageable")
    void pageable_keepsSort() {
        assertThat(PageResponse.pageable(0, 10, BY_NAME).getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    @DisplayName("of() mapeia os itens e preserva os totais da base, não os da página")
    void of_mapsAndKeepsTotals() {
        // 42 elementos no total, mas só 2 nesta página: o envelope precisa contar os 42, que é
        // o que a tela usa para dizer "1–2 de 42" sem inventar.
        var page = new PageImpl<>(List.of("ana", "zeca"), PageRequest.of(0, 2, BY_NAME), 42);

        PageResponse<String> out = PageResponse.of(page, String::toUpperCase);

        assertThat(out.items()).containsExactly("ANA", "ZECA");
        assertThat(out.total()).isEqualTo(42);
        assertThat(out.totalPages()).isEqualTo(21);
        assertThat(out.page()).isZero();
        assertThat(out.hasPrevious()).isFalse();
        assertThat(out.hasNext()).isTrue();
    }

    @Test
    @DisplayName("última página não anuncia próxima")
    void of_lastPage_hasNoNext() {
        var page = new PageImpl<>(List.of("z"), PageRequest.of(20, 2, BY_NAME), 41);

        PageResponse<String> out = PageResponse.of(page, s -> s);

        assertThat(out.hasNext()).isFalse();
        assertThat(out.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("lista vazia devolve envelope coerente em vez de nulo")
    void of_empty() {
        PageResponse<String> out = PageResponse.of(
                new PageImpl<String>(List.of(), PageRequest.of(0, 20, BY_NAME), 0), s -> s);

        assertThat(out.items()).isEmpty();
        assertThat(out.total()).isZero();
        assertThat(out.totalPages()).isZero();
        assertThat(out.hasNext()).isFalse();
        assertThat(out.hasPrevious()).isFalse();
    }
}
