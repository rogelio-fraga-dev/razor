package com.razor.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "razor")
public class RazorProperties {
    private Admin admin = new Admin();
    private Evolution evolution = new Evolution();
    private OpenAI openai = new OpenAI();
    private Schedule schedule = new Schedule();
    /** Textos e links do menu (opcional; se vazio usamos valores padrão no código) */
    private Menu menu = new Menu();
    private Onboarding onboarding = new Onboarding();
    private Security security = new Security();
    private Webhook webhook = new Webhook();

    @Data
    public static class Webhook {
        /**
         * Token exigido no webhook da Evolution (header {@code X-Webhook-Token} ou {@code ?token=}).
         * Vazio (padrao) = webhook DESABILITADO (fail-closed), pelo mesmo criterio do onboarding:
         * sem segredo configurado ninguem injeta mensagem forjada no sistema.
         */
        private String token = "";
    }

    @Data
    public static class Admin {
        private java.util.List<String> phones = new java.util.ArrayList<>();
    }

    @Data
    public static class Onboarding {
        /**
         * Chave exigida no header do endpoint de provisionamento de tenants. Vazio (padrao)
         * desabilita o onboarding (fail-closed): o endpoint rejeita qualquer requisicao.
         */
        private String apiKey = "";
    }

    @Data
    public static class Security {
        /**
         * Segredo HMAC para assinar os JWT do painel web. Vazio/curto (&lt;32 chars) faz o
         * JwtService gerar uma chave aleatoria no boot (tokens invalidam ao reiniciar) — defina
         * RAZOR_JWT_SECRET em producao.
         */
        private String jwtSecret = "";
        private long jwtExpirationMinutes = 720; // 12h
        /**
         * Flag {@code Secure} dos cookies do painel (sessao JWT e XSRF-TOKEN). Ligado por padrao:
         * cookie de sessao nunca deve trafegar em HTTP puro. Desligue ({@code RAZOR_COOKIE_SECURE=false})
         * apenas em dev servido por http fora de localhost — Chrome/Firefox ja aceitam Secure em
         * http://localhost, o Safari nao.
         */
        private boolean cookieSecure = true;
        /** Origens permitidas no CORS do painel (SPA). */
        private java.util.List<String> corsAllowedOrigins =
                new java.util.ArrayList<>(java.util.List.of("http://localhost:5173", "http://localhost:3000"));

        private RateLimit rateLimit = new RateLimit();
    }

    /**
     * Freio geral da API, por IP e por janela. Ate aqui so o login tinha limite — o resto de
     * {@code /api/admin/**} e o webhook aceitavam quantas requisicoes viessem, o que deixa
     * varredura de dados e abuso do webhook sem custo nenhum para quem tenta.
     */
    @Data
    public static class RateLimit {
        /** Desligar so faz sentido em teste; em producao um limite alto e melhor que nenhum. */
        private boolean enabled = true;
        /** Tamanho da janela, em segundos. */
        private int windowSeconds = 60;
        /**
         * Requisicoes por janela em {@code /api/admin/**}. Folgado de proposito: o painel abre
         * varias chamadas ao montar uma tela, e o objetivo aqui e barrar varredura, nao uso.
         */
        private int adminPerMinute = 240;
        /**
         * Requisicoes por janela no webhook. A Evolution manda uma por mensagem recebida; uma
         * barbearia movimentada nao chega perto disso, um laco de injecao passa facil.
         */
        private int webhookPerMinute = 120;
        /**
         * Requisicoes por janela nas rotas publicas do site ({@code /api/site/**}): a pagina, as
         * opcoes e a grade de horarios. Um visitante trocando de dia na grade gasta poucas.
         */
        private int sitePerMinute = 120;
        /** Agendamentos online (POST) aceitos por IP dentro de {@link #bookingWindowSeconds}. */
        private int bookingPerWindow = 10;
        /** Janela do limite de agendamento online, em segundos (1h). */
        private int bookingWindowSeconds = 3600;
    }

    @Data
    public static class Evolution {
        private String baseUrl;
        private String instanceName;
        private String apiKey;
    }

    @Data
    public static class OpenAI {
        private String apiKey;
        private String model;
        private String visionModel;
        private String whisperModel;
        private Double temperature;
    }

    @Data
    public static class Schedule {
        private Integer delayMinutes;
        private Integer batchSize;
        private Long delayBetweenMessages;
    }

    @Data
    public static class Menu {
        private String addressText = "";
        private String addressMapsUrl = "";
        private String servicesText = "";
        private String scheduleUrl = "";
        private String instagramUrl = "";
    }
}
