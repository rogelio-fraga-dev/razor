package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import com.razor.api.util.AppointmentDates;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Chave da OpenAI e modelos pelo console (V51). A chave e so de escrita: entra, e cifrada e nunca
 * mais sai inteira (o console ve os 4 ultimos). Modelo vazio = o do ambiente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAiSettingsService {

    private static final Pattern MODEL = Pattern.compile("[a-z0-9][a-z0-9.:\\-]{1,59}");
    private static final Pattern KEY = Pattern.compile("sk-[A-Za-z0-9_\\-]{20,200}");

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final AiRuntime runtime;
    private final RazorProperties properties;

    public record View(boolean secretsEnabled, String keySource, String keyLast4, String chatModel,
                       String escalationModel, String transcribeModel, String envChatModel,
                       String envEscalationModel, String envTranscribeModel, LocalDateTime updatedAt) {
    }

    /** {@code apiKey} nulo = mantem; {@code clearKey} = volta para a chave do ambiente. */
    public record Update(String apiKey, boolean clearKey, String chatModel, String escalationModel, String transcribeModel) {
    }

    public record TestResult(boolean ok, String message) {
    }

    public View view() {
        var s = runtime.current();
        var env = properties.getOpenai();
        var updated = jdbc.query("select updated_at from platform_settings where id = 1",
                (rs, i) -> rs.getTimestamp(1));
        Timestamp t = updated.isEmpty() ? null : updated.get(0);
        return new View(cipher.enabled(), s.keySource().name(), s.keyLast4(), s.chatModelName(), s.escalationModelName(),
                s.transcribeModelName(), env.getModel(), env.getEscalationModel(), env.getWhisperModel(),
                t == null ? null : t.toLocalDateTime());
    }

    public View update(Update u, UUID by) {
        if (!cipher.enabled()) throw new SecretCipher.ConsoleDisabledException();
        String chat = model(u.chatModel());
        String esc = model(u.escalationModel());
        String tr = model(u.transcribeModel());
        String newKey = u.apiKey() == null || u.apiKey().isBlank() ? null : u.apiKey().trim();
        if (newKey != null && !KEY.matcher(newKey).matches()) {
            throw new IllegalArgumentException("Chave com formato inesperado (deve começar com sk-).");
        }
        jdbc.update("insert into platform_settings (id) values (1) on conflict (id) do nothing");
        if (newKey != null) {
            jdbc.update("update platform_settings set openai_key_enc = ?, openai_key_last4 = ? where id = 1",
                    cipher.encrypt(newKey), newKey.substring(newKey.length() - 4));
        } else if (u.clearKey()) {
            jdbc.update("update platform_settings set openai_key_enc = null, openai_key_last4 = null where id = 1");
        }
        jdbc.update("update platform_settings set chat_model = ?, escalation_model = ?, transcribe_model = ?, "
                + "updated_at = ?, updated_by = ? where id = 1", chat, esc, tr,
                Timestamp.valueOf(LocalDateTime.now(AppointmentDates.ZONE)), by);
        runtime.reload();
        return view();
    }

    /**
     * Confere uma chave (a informada ou a em uso) listando os modelos — chamada gratuita. Nunca
     * devolve a resposta crua (pode ecoar a chave).
     */
    public TestResult test(String candidate) {
        String key = candidate == null || candidate.isBlank() ? runtime.current().apiKey() : candidate.trim();
        if (key == null || key.isBlank()) return new TestResult(false, "Nenhuma chave configurada.");
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + key).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return switch (r.statusCode()) {
                case 200 -> new TestResult(true, "Chave válida.");
                case 401 -> new TestResult(false, "Chave recusada pela OpenAI (inválida ou revogada).");
                case 429 -> new TestResult(false, "Limite ou cota da conta esgotados.");
                default -> new TestResult(false, "A OpenAI respondeu HTTP " + r.statusCode() + ".");
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestResult(false, "Teste interrompido.");
        } catch (Exception e) {
            return new TestResult(false, "Não consegui falar com a OpenAI (" + e.getClass().getSimpleName() + ").");
        }
    }

    private static String model(String m) {
        if (m == null || m.isBlank()) return null;
        String t = m.trim().toLowerCase();
        if (!MODEL.matcher(t).matches()) throw new IllegalArgumentException("Nome de modelo inválido: " + m);
        return t;
    }
}
