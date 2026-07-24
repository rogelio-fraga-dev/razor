package com.razor.api.state;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Estado efemero da operacao (dedup de mensagem, pausa de cliente, passo da conversa...).
 *
 * <p><b>Por que existe:</b> hoje esse estado vive em {@code ConcurrentHashMap} dentro dos
 * services — some no restart e impede rodar mais de uma instancia. Esta porta isola o
 * armazenamento para que a troca por Redis (Fase 6) seja um adaptador novo, sem tocar na
 * logica de conversa.
 *
 * <p><b>Contrato:</b> valores sao serializados em JSON nos dois adaptadores (memoria e Redis)
 * de proposito — assim um objeto que nao serializa quebra ja em desenvolvimento, e nao so
 * quando ligarmos o Redis em producao.
 *
 * <p>Chaves devem ser escopadas por tenant pelo chamador (ver {@link StateKeys}).
 */
public interface StateStore {

    /** Grava (ou sobrescreve) um valor com tempo de vida. */
    <T> void put(String key, T value, Duration ttl);

    /** Le um valor ainda valido; vazio se ausente ou expirado. */
    <T> Optional<T> get(String key, Class<T> type);

    /** Le uma lista ainda valida; vazio se ausente ou expirada. */
    <T> Optional<java.util.List<T>> getList(String key, Class<T> elementType);

    void remove(String key);

    /** Chaves vivas que comecam com o prefixo — usado para listar (ex.: clientes pausados). */
    Set<String> keysWithPrefix(String prefix);
}
