package com.razor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StrictSecretsValidatorTest {

    private static final String JWT_OK = "k9Qz7mXv2LpR8sTn4WbY6cHd1FgJ3aE5uIo0PlKjZx";
    private static final String TOKEN_OK = "Hq4Vn8Rt2Ms6Lp0Kx3Bz7Cw1Yd5Fe9Gu";

    private MockEnvironment strictEnvWithGoodSecrets() {
        return new MockEnvironment()
                .withProperty(StrictSecretsValidator.STRICT_PROPERTY, "true")
                .withProperty("razor.security.jwt-secret", JWT_OK)
                .withProperty("razor.webhook.token", TOKEN_OK)
                .withProperty("razor.onboarding.api-key", "")
                .withProperty("razor.evolution.api-key", "Ev0lut10nK3y-aleat0r1a")
                .withProperty("spring.datasource.password", "S3nh4-F0rte-D0-B4nc0");
    }

    @Test
    @DisplayName("sem modo estrito (dev): nada é validado, defaults locais sobem")
    void notStrict_skipsValidation() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("razor.security.jwt-secret", "")
                .withProperty("razor.evolution.api-key", "barberbot");
        assertThatCode(() -> new StrictSecretsValidator(env).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("modo estrito com segredos fortes: sobe")
    void strict_goodSecrets_ok() {
        assertThatCode(() -> new StrictSecretsValidator(strictEnvWithGoodSecrets()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("placeholder do .env.prod-example (TROCAR_...) é recusado mesmo com 32+ caracteres")
    void strict_placeholder_fails() {
        MockEnvironment env = strictEnvWithGoodSecrets()
                .withProperty("razor.security.jwt-secret", "TROCAR_gere_um_segredo_de_no_minimo_48_caracteres");
        assertThatThrownBy(() -> new StrictSecretsValidator(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZOR_JWT_SECRET")
                .hasMessageNotContaining("TROCAR_gere");
    }

    @Test
    @DisplayName("defaults de dev (chave 'barberbot', token curto, segredo vazio) são todos apontados")
    void strict_devDefaults_fail() {
        MockEnvironment env = strictEnvWithGoodSecrets()
                .withProperty("razor.evolution.api-key", "barberbot")
                .withProperty("razor.webhook.token", "dev-webhook-token")
                .withProperty("razor.security.jwt-secret", "")
                .withProperty("spring.datasource.password", "barberbot_pass");
        var problems = new StrictSecretsValidator(env).validate();
        assertThat(String.join("\n", problems))
                .contains("EVOLUTION_API_KEY", "WEBHOOK_TOKEN", "RAZOR_JWT_SECRET", "SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    @DisplayName("profile prod liga o modo estrito sem precisar da flag")
    void prodProfile_enablesStrict() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThat(new StrictSecretsValidator(env).isStrict()).isTrue();
    }
}
