package com.razor.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Trava de boot para producao: recusa subir com segredo vazio, curto ou de exemplo.
 *
 * <p><b>Opt-in</b> — so age com o profile {@code prod} ativo ou com
 * {@code razor.security.strict-secrets=true} (env {@code RAZOR_STRICT_SECRETS=true}, ligado pelo
 * {@code docker-compose.prod.yml}). Em dev nada muda: os defaults locais ({@code barberbot},
 * {@code dev-webhook-token}...) continuam funcionando no {@code docker compose up} e no
 * {@code ./mvnw test}.
 *
 * <p>Por que existe: o JwtService aceita qualquer string com 32+ caracteres, entao o placeholder
 * {@code TROCAR_gere_um_segredo_de_no_minimo_48_caracteres} do {@code .env.prod-example} "funciona"
 * — e um segredo publicado no repositorio assinaria os tokens de producao. Idem para a chave da
 * Evolution ({@code barberbot}) e a senha do banco do compose local.
 *
 * <p>A mensagem de erro cita so o NOME da propriedade e o motivo; o valor nunca e impresso.
 */
@Slf4j
@Component
public class StrictSecretsValidator implements InitializingBean {

    static final String STRICT_PROPERTY = "razor.security.strict-secrets";

    /** Tamanho minimo de segredo gerado (JWT, webhook, onboarding). */
    static final int MIN_TOKEN_LENGTH = 32;
    /** Senha do banco e chave da Evolution: exigencia menor, mas nada de senha de exemplo. */
    static final int MIN_PASSWORD_LENGTH = 16;

    /** Trechos que denunciam valor de exemplo/desenvolvimento (comparacao sem caixa). */
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "trocar", "troque", "dev-", "changeme", "change-me", "change_me", "example", "exemplo",
            "placeholder", "dummy", "secret123", "barberbot");

    private final Environment env;

    public StrictSecretsValidator(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isStrict()) {
            return;
        }
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Segredos de producao invalidos (" + STRICT_PROPERTY
                    + " ativo). Corrija no .env e suba de novo:\n - " + String.join("\n - ", problems));
        }
        log.info("[SECURITY] Validacao estrita de segredos OK.");
    }

    boolean isStrict() {
        return env.acceptsProfiles(Profiles.of("prod"))
                || Boolean.parseBoolean(env.getProperty(STRICT_PROPERTY, "false").trim());
    }

    /** Lista de problemas (nome da variavel + motivo), sem valores. Vazia = tudo certo. */
    List<String> validate() {
        List<String> problems = new ArrayList<>();
        check(problems, "razor.security.jwt-secret", "RAZOR_JWT_SECRET", MIN_TOKEN_LENGTH, true);
        check(problems, "razor.webhook.token", "WEBHOOK_TOKEN", MIN_TOKEN_LENGTH, true);
        // Onboarding vazio = endpoint desligado (fail-closed), o que e um estado valido em producao.
        check(problems, "razor.onboarding.api-key", "ONBOARDING_API_KEY", MIN_TOKEN_LENGTH, false);
        check(problems, "razor.evolution.api-key", "EVOLUTION_API_KEY", MIN_PASSWORD_LENGTH, true);
        check(problems, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD", MIN_PASSWORD_LENGTH, true);
        return problems;
    }

    private void check(List<String> problems, String property, String envName, int minLength, boolean required) {
        String value = env.getProperty(property);
        String label = envName + " (" + property + ")";
        if (value == null || value.isBlank()) {
            if (required) {
                problems.add(label + ": vazio — obrigatorio em producao");
            }
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength) {
            problems.add(label + ": curto demais (minimo " + minLength + " caracteres)");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                problems.add(label + ": parece valor de exemplo/desenvolvimento — gere um aleatorio");
                break;
            }
        }
    }
}
