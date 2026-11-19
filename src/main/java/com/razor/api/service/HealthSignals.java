package com.razor.api.service;

import com.razor.api.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sinais de saude em memoria (V50): quem chama a IA e quem envia pelo WhatsApp registra aqui o
 * sucesso ou a falha, por barbearia; o motor de tarefas registra o batimento. O
 * {@code SystemHealthMonitor} le estes sinais periodicamente e abre/fecha incidentes.
 *
 * <p>Em memoria de proposito: e barato (nada de banco em cada mensagem) e o que importa e a
 * tendencia recente. Reiniciar zera os contadores — e por isso o monitor so RESOLVE um incidente
 * quando ve um sucesso depois da falha, nunca so porque o contador esta zerado.
 *
 * <p>Nunca guarda a mensagem crua do provedor: so a categoria ({@link #classifyAiError}), que nao
 * carrega pedaco de chave nem telefone.
 */
@Component
public class HealthSignals {

    /** Categorias de falha da IA, em texto para o dono/operador. */
    public static final String AI_QUOTA = "cota da OpenAI esgotada ou limite de uso atingido";
    public static final String AI_AUTH = "chave da OpenAI inválida ou sem permissão";
    public static final String AI_TIMEOUT = "OpenAI demorando demais para responder";
    public static final String AI_OTHER = "erro ao chamar a OpenAI";

    /** Estado de um canal (IA ou envio) de uma barbearia. Imutavel: trocado inteiro a cada evento. */
    public record ChannelState(int consecutiveFailures, Instant lastFailureAt, Instant lastSuccessAt,
                               String lastErrorKind, long totalFailures) {
        static final ChannelState EMPTY = new ChannelState(0, null, null, null, 0);

        ChannelState failure(Instant at, String kind) {
            return new ChannelState(consecutiveFailures + 1, at, lastSuccessAt, kind, totalFailures + 1);
        }

        ChannelState success(Instant at) {
            return new ChannelState(0, lastFailureAt, at, lastErrorKind, totalFailures);
        }

        /** Houve sucesso depois da ultima falha (o problema passou). */
        public boolean recovered() {
            return consecutiveFailures == 0 && lastSuccessAt != null;
        }
    }

    private final Map<UUID, ChannelState> ai = new ConcurrentHashMap<>();
    private final Map<UUID, ChannelState> send = new ConcurrentHashMap<>();
    /** Verificacoes seguidas com o WhatsApp da barbearia fora (o monitor que conta). */
    private final Map<UUID, Integer> whatsappDown = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Instant startedAt;
    private volatile Instant schedulerBeat;

    public HealthSignals() {
        this(Clock.systemUTC());
    }

    HealthSignals(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    // --- IA -----------------------------------------------------------------------------------

    public void aiSuccess() {
        currentTenant().ifPresent(t -> ai.compute(t, (k, s) -> (s == null ? ChannelState.EMPTY : s).success(clock.instant())));
    }

    public void aiFailure(Throwable error) {
        String kind = classifyAiError(error);
        currentTenant().ifPresent(t -> ai.compute(t, (k, s) -> (s == null ? ChannelState.EMPTY : s).failure(clock.instant(), kind)));
    }

    public ChannelState ai(UUID shop) {
        return ai.getOrDefault(shop, ChannelState.EMPTY);
    }

    // --- Envio pelo WhatsApp ------------------------------------------------------------------

    public void sendSuccess(UUID shop) {
        if (shop != null) send.compute(shop, (k, s) -> (s == null ? ChannelState.EMPTY : s).success(clock.instant()));
    }

    public void sendFailure(UUID shop) {
        if (shop != null) {
            send.compute(shop, (k, s) -> (s == null ? ChannelState.EMPTY : s)
                    .failure(clock.instant(), "a Evolution recusou o envio ou não respondeu"));
        }
    }

    public ChannelState send(UUID shop) {
        return send.getOrDefault(shop, ChannelState.EMPTY);
    }

    // --- WhatsApp (estado da instancia) -------------------------------------------------------

    /** Mais uma verificacao com o WhatsApp fora; devolve quantas seguidas. */
    public int whatsappDownCheck(UUID shop) {
        return whatsappDown.merge(shop, 1, Integer::sum);
    }

    public void whatsappUp(UUID shop) {
        whatsappDown.remove(shop);
    }

    // --- Motor de tarefas ---------------------------------------------------------------------

    public void schedulerBeat() {
        schedulerBeat = clock.instant();
    }

    /** Ultimo batimento do motor; antes do primeiro, o instante do boot (da folga para subir). */
    public Instant lastSchedulerBeat() {
        Instant beat = schedulerBeat;
        return beat != null ? beat : startedAt;
    }

    public Instant now() {
        return clock.instant();
    }

    /**
     * Categoria da falha da IA pelo texto/tipo da excecao — nunca devolve o texto cru (o erro da
     * OpenAI para chave errada inclui um pedaco da chave).
     */
    static String classifyAiError(Throwable error) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = error; t != null && all.length() < 4000; t = t.getCause()) {
            all.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
            if (t.getCause() == t) break;
        }
        String s = all.toString().toLowerCase(Locale.ROOT);
        if (s.contains("insufficient_quota") || s.contains("quota") || s.contains("429") || s.contains("rate limit")) {
            return AI_QUOTA;
        }
        if (s.contains("401") || s.contains("invalid_api_key") || s.contains("incorrect api key")
                || s.contains("unauthorized") || s.contains("403")) {
            return AI_AUTH;
        }
        if (s.contains("timeout") || s.contains("timed out")) {
            return AI_TIMEOUT;
        }
        return AI_OTHER;
    }

    private static Optional<UUID> currentTenant() {
        return TenantContext.isSet() ? Optional.of(TenantContext.getCurrent()) : Optional.empty();
    }
}
