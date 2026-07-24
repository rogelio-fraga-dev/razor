package com.razor.api.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador Redis do {@link StateStore}: mesmo contrato do {@link InMemoryStateStore}, mas o
 * estado passa a ser <b>compartilhado entre instancias</b> e sobrevive ao restart. Ligado por
 * {@code razor.state.store=redis} — a troca e so de bean, sem tocar nos services.
 *
 * <p>Guarda JSON como string com <b>TTL nativo</b> do Redis (SET ... PX): chaves expiradas
 * somem sozinhas, entao aqui nao ha rotina de limpeza como no adaptador em memoria.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "razor.state.store", havingValue = "redis")
public class RedisStateStore implements StateStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            // Igual ao in-memory: erro de serializacao e bug, explode cedo.
            throw new IllegalArgumentException("Valor de estado nao serializavel para a chave " + key, e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("[STATE] Valor ilegivel em '{}' — descartado: {}", key, e.getMessage());
            redis.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return Optional.of(objectMapper.readValue(json, listType));
        } catch (Exception e) {
            log.warn("[STATE] Lista ilegivel em '{}' — descartada: {}", key, e.getMessage());
            redis.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public void remove(String key) {
        redis.delete(key);
    }

    @Override
    public Set<String> keysWithPrefix(String prefix) {
        // SCAN incremental, nunca KEYS — KEYS varre tudo de uma vez e trava o Redis em producao.
        Set<String> found = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(256).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                found.add(cursor.next());
            }
        }
        return found;
    }
}
