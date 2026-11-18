package com.razor.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pools do {@code @Async}, um para cada tipo de trabalho.
 *
 * <ul>
 *   <li><b>applicationTaskExecutor / taskExecutor</b> — o padrao (sem qualificador): processamento
 *       das mensagens do webhook. Mesmos numeros do pool que o Spring Boot criava sozinho (8
 *       threads). Declarado aqui porque, ao existir QUALQUER outro executor, o Boot deixa de criar
 *       o dele — e o webhook passaria a cair no pool de disparos.</li>
 *   <li><b>broadcastExecutor</b> — disparos em massa (painel e WhatsApp do dono). Poucas threads
 *       e fila limitada: cada disparo segura a thread por minutos (pausa anti-bloqueio entre
 *       envios), e sem limite um dono apertando "enviar" varias vezes empilharia disparos sem fim.
 *       Fila cheia recusa ({@code TaskRejectedException} → 503).</li>
 * </ul>
 */
@Configuration
public class AsyncConfig {

    public static final String BROADCAST_EXECUTOR = "broadcastExecutor";

    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setThreadNamePrefix("task-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }

    @Bean(name = BROADCAST_EXECUTOR)
    public ThreadPoolTaskExecutor broadcastExecutor(
            @Value("${razor.broadcast.pool-size:2}") int poolSize,
            @Value("${razor.broadcast.queue-capacity:10}") int queueCapacity) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(poolSize);
        ex.setMaxPoolSize(poolSize);
        ex.setQueueCapacity(queueCapacity);
        ex.setThreadNamePrefix("disparo-");
        ex.initialize();
        return ex;
    }
}
