package com.razor.api.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato do StateStore. Estes testes valem para QUALQUER adaptador — quando o Redis entrar,
 * a mesma bateria deve passar contra ele (garantia de que a troca nao muda comportamento).
 */
class InMemoryStateStoreTest {

    private final InMemoryStateStore store = new InMemoryStateStore(new ObjectMapper());

    @Test
    @DisplayName("grava e lê um valor dentro do TTL")
    void putThenGet() {
        store.put("k1", "valor", Duration.ofMinutes(5));

        assertThat(store.get("k1", String.class)).contains("valor");
    }

    @Test
    @DisplayName("valor expirado não é devolvido")
    void expiredIsGone() throws InterruptedException {
        store.put("k2", Boolean.TRUE, Duration.ofMillis(30));
        Thread.sleep(60);

        assertThat(store.get("k2", Boolean.class)).isEmpty();
    }

    @Test
    @DisplayName("chave ausente devolve vazio (não lança)")
    void missingIsEmpty() {
        assertThat(store.get("nao-existe", String.class)).isEmpty();
    }

    @Test
    @DisplayName("remove apaga o valor")
    void removeDeletes() {
        store.put("k3", "x", Duration.ofMinutes(5));
        store.remove("k3");

        assertThat(store.get("k3", String.class)).isEmpty();
    }

    @Test
    @DisplayName("listas sobrevivem à serialização (round-trip)")
    void listRoundTrip() {
        store.put("k4", List.of("a", "b", "c"), Duration.ofMinutes(5));

        assertThat(store.getList("k4", String.class)).contains(List.of("a", "b", "c"));
    }

    @Test
    @DisplayName("keysWithPrefix devolve só as chaves vivas do prefixo")
    void prefixScan() {
        store.put("pause:t1:111", Boolean.TRUE, Duration.ofMinutes(5));
        store.put("pause:t1:222", Boolean.TRUE, Duration.ofMinutes(5));
        store.put("pause:t2:333", Boolean.TRUE, Duration.ofMinutes(5)); // outro tenant
        store.put("dedup:abc", Boolean.TRUE, Duration.ofMinutes(5));    // outro domínio

        assertThat(store.keysWithPrefix("pause:t1:"))
                .containsExactlyInAnyOrder("pause:t1:111", "pause:t1:222");
    }

    @Test
    @DisplayName("prefixo ignora chaves expiradas")
    void prefixSkipsExpired() throws InterruptedException {
        store.put("pause:t1:111", Boolean.TRUE, Duration.ofMillis(30));
        store.put("pause:t1:222", Boolean.TRUE, Duration.ofMinutes(5));
        Thread.sleep(60);

        assertThat(store.keysWithPrefix("pause:t1:")).containsExactly("pause:t1:222");
    }

    @Test
    @DisplayName("valor não serializável falha na hora (e não só quando ligarmos o Redis)")
    void nonSerializableFailsFast() {
        assertThatThrownBy(() -> store.put("k5", new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                throw new IllegalStateException("boom");
            }
        }, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
