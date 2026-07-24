package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.state.RedisStateStore;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida o {@link RedisStateStore} contra um Redis real (Testcontainers): round-trip de valor
 * e de lista, expiracao por TTL nativo, remocao e varredura por prefixo com SCAN. Constroi o
 * adaptador direto (sem subir Spring/Postgres) para ficar leve e focado.
 *
 * Pulado sem Docker (dev local); roda no CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RedisStateStore contra Redis real")
class RedisStateStoreIT {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static RedisStateStore store;

    record Sample(String name, int n) {}

    @BeforeAll
    static void setUp() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        store = new RedisStateStore(template, new ObjectMapper());
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) factory.destroy();
    }

    @Test
    @DisplayName("put/get faz round-trip do objeto")
    void putGet_roundTrip() {
        store.put("obj:1", new Sample("Diego", 7), Duration.ofMinutes(5));

        assertThat(store.get("obj:1", Sample.class)).contains(new Sample("Diego", 7));
    }

    @Test
    @DisplayName("get de chave ausente devolve vazio")
    void get_absent_empty() {
        assertThat(store.get("nao:existe", Sample.class)).isEmpty();
    }

    @Test
    @DisplayName("getList faz round-trip da lista")
    void getList_roundTrip() {
        store.put("lst:1", List.of(new Sample("a", 1), new Sample("b", 2)), Duration.ofMinutes(5));

        assertThat(store.getList("lst:1", Sample.class))
                .contains(List.of(new Sample("a", 1), new Sample("b", 2)));
    }

    @Test
    @DisplayName("remove apaga a chave")
    void remove_deletes() {
        store.put("del:1", new Sample("x", 0), Duration.ofMinutes(5));
        store.remove("del:1");

        assertThat(store.get("del:1", Sample.class)).isEmpty();
    }

    @Test
    @DisplayName("TTL nativo expira a chave")
    void ttl_expires() throws InterruptedException {
        store.put("ttl:1", new Sample("efemero", 1), Duration.ofSeconds(1));
        assertThat(store.get("ttl:1", Sample.class)).isPresent();

        Thread.sleep(1300);

        assertThat(store.get("ttl:1", Sample.class)).isEmpty();
    }

    @Test
    @DisplayName("keysWithPrefix devolve so as chaves vivas do prefixo (via SCAN)")
    void keysWithPrefix_matchesPrefix() {
        store.put("pause:tenant-a:111", new Sample("p", 1), Duration.ofMinutes(5));
        store.put("pause:tenant-a:222", new Sample("p", 2), Duration.ofMinutes(5));
        store.put("pause:tenant-b:333", new Sample("p", 3), Duration.ofMinutes(5));

        assertThat(store.keysWithPrefix("pause:tenant-a:"))
                .containsExactlyInAnyOrder("pause:tenant-a:111", "pause:tenant-a:222")
                .doesNotContain("pause:tenant-b:333");
    }
}
