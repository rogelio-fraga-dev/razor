package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import com.razor.api.service.ChatEscalation;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Chave e modelos da OpenAI em uso agora (V51). O console pode trocar sem reiniciar: o que esta em
 * {@code platform_settings} vale; o que estiver vazio la vem do ambiente ({@code OPENAI_API_KEY},
 * {@code OPENAI_MODEL}...). Os modelos so sao recriados quando algo muda.
 */
@Slf4j
@Component
public class AiRuntime {

    public enum Source { ENV, CONSOLE }

    /** Configuracao efetiva + os modelos prontos. */
    public record Snapshot(String apiKey, Source keySource, String chatModelName, String escalationModelName,
                           String transcribeModelName, OpenAiChatModel chat, OpenAiChatModel escalation) {
        public String keyLast4() {
            return apiKey == null || apiKey.length() < 4 ? null : apiKey.substring(apiKey.length() - 4);
        }
    }

    private final RazorProperties properties;
    private final SecretCipher cipher;
    private final JdbcTemplate jdbc;
    private final OpenAiChatModel envChat;
    private final ChatEscalation envEscalation;
    private volatile Snapshot snapshot;

    public AiRuntime(RazorProperties properties, SecretCipher cipher, JdbcTemplate jdbc, OpenAiChatModel envChat,
                     ChatEscalation envEscalation) {
        this.properties = properties;
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.envChat = envChat;
        this.envEscalation = envEscalation;
    }

    public Snapshot current() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                if (snapshot == null) reload();
                s = snapshot;
            }
        }
        return s;
    }

    public OpenAiChatModel chat() {
        return current().chat();
    }

    public OpenAiChatModel escalation() {
        return current().escalation();
    }

    /** Rele a configuracao (chamado depois de salvar no console). */
    public synchronized void reload() {
        var env = properties.getOpenai();
        String key = env.getApiKey();
        Source source = Source.ENV;
        String chat = blank(env.getModel(), "gpt-4o-mini");
        String esc = blank(env.getEscalationModel(), "gpt-4o");
        String tr = blank(env.getWhisperModel(), "gpt-4o-mini-transcribe");
        try {
            List<String[]> rows = jdbc.query(
                    "select openai_key_enc, chat_model, escalation_model, transcribe_model from platform_settings where id = 1",
                    (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
            if (!rows.isEmpty()) {
                String[] r = rows.get(0);
                if (r[0] != null && cipher.enabled()) {
                    key = cipher.decrypt(r[0]);
                    source = Source.CONSOLE;
                }
                chat = blank(r[1], chat);
                esc = blank(r[2], esc);
                tr = blank(r[3], tr);
            }
        } catch (Exception e) {
            // Sem a tabela (boot antes do Flyway) ou segredo ilegivel: segue com o ambiente.
            log.warn("[IA] Configuracao do console indisponivel, usando o ambiente: {}", e.getClass().getSimpleName());
        }
        Snapshot old = snapshot;
        boolean envKey = Objects.equals(key, env.getApiKey());
        OpenAiChatModel chatModel = reuse(old, key, chat, true);
        if (chatModel == null) {
            chatModel = envKey && chat.equals(blank(env.getModel(), "gpt-4o-mini")) && envChat != null ? envChat : build(key, chat);
        }
        OpenAiChatModel escModel = reuse(old, key, esc, false);
        if (escModel == null) {
            escModel = envKey && esc.equals(blank(env.getEscalationModel(), "gpt-4o")) && envEscalation != null
                    && envEscalation.model() != null ? envEscalation.model() : build(key, esc);
        }
        snapshot = new Snapshot(key, source, chat, esc, tr, chatModel, escModel);
        if (old != null) {
            log.info("[IA] Configuracao recarregada: chave do {} (...{}), chat={}, forte={}, audio={}",
                    source == Source.CONSOLE ? "console" : "ambiente", snapshot.keyLast4(), chat, esc, tr);
        }
    }

    private static OpenAiChatModel reuse(Snapshot old, String key, String model, boolean main) {
        if (old == null || !Objects.equals(old.apiKey(), key)) return null;
        if (main && model.equals(old.chatModelName())) return old.chat();
        if (!main && model.equals(old.escalationModelName())) return old.escalation();
        return null;
    }

    private OpenAiChatModel build(String key, String model) {
        return OpenAiChatModel.builder()
                .apiKey(key)
                .modelName(model)
                .temperature(properties.getOpenai().getTemperature())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    private static String blank(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
