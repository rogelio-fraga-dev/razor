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
    private Monitoring monitoring = new Monitoring();
    private Storage storage = new Storage();

    /**
     * Monitoramento de saude por barbearia (V50): WhatsApp, IA, envios, fila e motor de tarefas.
     * O monitor e o aviso no painel ficam ligados por padrao; o aviso ao OPERADOR da plataforma e
     * opt-in (webhook e/ou log), desligado por padrao.
     */
    @Data
    public static class Monitoring {
        /** Liga a verificacao periodica (padrao: ligada). */
        private boolean enabled = true;
        /** Intervalo entre verificacoes, em ms. */
        private long intervalMs = 120_000;
        /** Falhas SEGUIDAS da IA para abrir incidente (cota/chave invalida abre na primeira). */
        private int aiFailureThreshold = 3;
        /** Falhas SEGUIDAS de envio pelo WhatsApp para abrir incidente. */
        private int sendFailureThreshold = 3;
        /** Verificacoes seguidas com o WhatsApp fora para abrir incidente (evita alarme no pisca). */
        private int whatsappDownChecks = 2;
        /** Tarefa pendente atrasada ha mais que isto = fila parada. */
        private int queueStuckMinutes = 15;
        /** Motor de tarefas sem rodar ha mais que isto = parado (ele roda a cada 60s). */
        private int schedulerStaleMinutes = 5;
        /** Incidente de IA/envio sem falha nova ha este tempo e dado como resolvido. */
        private int staleResolveHours = 6;
        /** Intervalo minimo entre dois avisos ao dono pelo WhatsApp sobre o MESMO incidente. */
        private int ownerCooldownHours = 6;
        /** Intervalo minimo entre dois avisos ao operador sobre o mesmo incidente. */
        private int operatorCooldownMinutes = 60;
        /**
         * URL de webhook do operador (Slack/Discord/qualquer endpoint que aceite JSON). Vazio =
         * desligado. Recebe {@code {"text": ..., "content": ...}} — nunca segredo nem telefone.
         */
        private String operatorWebhookUrl = "";
        /** Alternativa sem infraestrutura: o aviso ao operador vira uma linha de log [OPERADOR]. */
        private boolean operatorLogAlerts = false;
    }

    /**
     * Onde ficam as fotos do site (V50). {@code db} (padrao) = no proprio Postgres, como na V45.
     * {@code s3} = bucket S3 compativel (AWS, MinIO, R2...). A URL publica nao muda:
     * {@code /api/site/img/{uuid}} continua servida pelo backend.
     */
    @Data
    public static class Storage {
        private String type = "db";
        private S3 s3 = new S3();

        @Data
        public static class S3 {
            private String bucket = "";
            private String region = "us-east-1";
            /** Endpoint customizado (MinIO/R2). Vazio = AWS. */
            private String endpoint = "";
            /** Prefixo das chaves dentro do bucket. */
            private String prefix = "site-images/";
            /** Credenciais: vazias = cadeia padrao da AWS (env AWS_*, perfil, IAM role). */
            private String accessKey = "";
            private String secretKey = "";
            /** Enderecamento por caminho (bucket na URL) — o MinIO exige. */
            private boolean pathStyle = false;
        }
    }

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
