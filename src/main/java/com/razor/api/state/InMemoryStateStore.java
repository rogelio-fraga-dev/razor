package com.razor.api.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador padrao do {@link StateStore}: guarda em memoria com expiracao por chave.
 * Mantem o comportamento historico (uma instancia, estado perdido no restart) enquanto o
 * adaptador Redis nao entra — a troca sera so de bean, sem mexer nos services.
 *
 * <p>Serializa em JSON como o Redis fara, para que um valor nao-serializavel falhe aqui.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "razor.state.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryStateStore implements StateStore {

    private record Entry(String json, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ObjectMapper objectMapper;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            entries.put(key, new Entry(objectMapper.writeValueAsString(value), Instant.now().plus(ttl)));
        } catch (Exception e) {
            // Falha de serializacao e erro de programacao — melhor explodir cedo do que
            // descobrir so ao ligar o Redis.
            throw new IllegalArgumentException("Valor de estado nao serializavel para a chave " + key, e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return alive(key).map(json -> {
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                log.warn("[STATE] Valor ilegivel em '{}' — descartado: {}", key, e.getMessage());
                entries.remove(key);
                return null;
            }
        });
    }

    @Override
    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        return alive(key).map(json -> {
            try {
                var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
                return objectMapper.<List<T>>readValue(json, listType);
            } catch (Exception e) {
                log.warn("[STATE] Lista ilegivel em '{}' — descartada: {}", key, e.getMessage());
                entries.remove(key);
                return null;
            }
        });
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    @Override
    public Set<String> keysWithPrefix(String prefix) {
        purgeExpired();
        return entries.keySet().stream().filter(k -> k.startsWith(prefix)).collect(Collectors.toSet());
    }

    /** Limpeza periodica — sem ela chaves expiradas ficariam ocupando memoria. */
    @Scheduled(fixedRate = 600000)
    public void purgeExpired() {
        entries.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private Optional<String> alive(String key) {
        Entry e = entries.get(key);
        if (e == null) return Optional.empty();
        if (e.isExpired()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.json());
    }
}
