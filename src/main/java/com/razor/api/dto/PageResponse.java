package com.razor.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Envelope das listas paginadas do painel.
 *
 * <p><b>Por que {@code total} vem junto.</b> Sem ele a tela nao consegue dizer "35 de 412" nem
 * desabilitar o botao de proxima pagina, e acabaria inventando — que e justamente o que este
 * projeto nao faz com numero. O custo e um {@code count(*)} por consulta, e o Postgres resolve
 * pelo indice que ja existe em {@code barbershop_id}.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    /** Tamanho de pagina padrao — cabe numa tela sem rolar demais. */
    public static final int DEFAULT_SIZE = 20;

    /**
     * Teto do tamanho de pagina. Existe porque {@code ?size=999999} devolveria a tabela inteira e
     * anularia a paginacao — o problema que ela veio resolver. Pedir mais nao da erro: entrega o
     * maximo, porque derrubar a requisicao por um parametro grande demais so atrapalharia quem
     * esta integrando.
     */
    public static final int MAX_SIZE = 100;

    /** Converte uma {@link Page} do Spring Data aplicando o mapeamento para o DTO da tela. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    /**
     * Monta o {@link Pageable} a partir dos parametros da requisicao, tolerando valores ausentes
     * ou fora de faixa em vez de recusar: pagina negativa vira a primeira, tamanho zero vira o
     * padrao e tamanho acima do teto e limitado.
     */
    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }
}
