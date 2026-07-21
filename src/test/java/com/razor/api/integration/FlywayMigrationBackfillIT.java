package com.razor.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida a cadeia de migracoes Flyway (V1..V3) contra um Postgres real e, em especial,
 * o backfill da V3: converte datas/horas legadas em VARCHAR ("hoje", "DD/MM", "HHhMM")
 * para colunas DATE/TIME tipadas, sem perder a regra de "proxima ocorrencia futura".
 *
 * Pulado automaticamente quando nao ha Docker (dev local), roda no CI (ubuntu com Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migracao V3: backfill de datas/horas legadas (VARCHAR -> DATE/TIME) em Postgres real")
class FlywayMigrationBackfillIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flywayTo(String targetVersion) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load();
    }

    @Test
    @DisplayName("cadeia migra sem erro; V3 converte legado e nunca guarda data passada")
    void migrationChain_convertsLegacyData() throws Exception {
        // 1) Migra ate a criacao da tabela (VARCHAR), antes da conversao tipada (V3)
        flywayTo("2.1").migrate();

        // Usa a data do PROPRIO banco para evitar divergencia de fuso com o host
        LocalDate dbToday;
        try (Connection c = openConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT CURRENT_DATE")) {
            rs.next();
            dbToday = rs.getObject(1, LocalDate.class);
        }
        LocalDate yesterday = dbToday.minusDays(1);
        LocalDate tomorrow = dbToday.plusDays(1);

        // 2) Insere linhas legadas (colunas ainda VARCHAR)
        insertLegacy(1L, "hoje", "10:00");
        insertLegacy(2L, ddmm(yesterday), "10h30"); // ja passou este ano -> deve rolar pra frente
        insertLegacy(3L, ddmm(tomorrow), "10h"); // futuro -> mantem
        insertLegacy(4L, null, null); // nulos permanecem nulos
        insertLegacy(5L, "sexta", "qualquer"); // lixo nao-parseavel -> NULL

        // 3) Aplica a V3 (conversao tipada + backfill)
        flywayTo("3").migrate();

        // 4a) As colunas viraram tipos fortes de verdade
        assertThat(columnType("appointment_date")).isEqualTo("date");
        assertThat(columnType("appointment_time")).isEqualTo("time without time zone");

        // 4b) Conversao dos valores
        assertThat(dateOf(1L)).isEqualTo(dbToday);
        assertThat(timeOf(1L)).isEqualTo(LocalTime.of(10, 0));

        // "ontem" (DD/MM ja passado) nunca resulta em data no passado
        assertThat(dateOf(2L)).isAfter(dbToday);
        assertThat(dateOf(2L).getDayOfMonth()).isEqualTo(yesterday.getDayOfMonth());
        assertThat(dateOf(2L).getMonthValue()).isEqualTo(yesterday.getMonthValue());
        assertThat(timeOf(2L)).isEqualTo(LocalTime.of(10, 30));

        // "amanha" (DD/MM futuro) mantem exatamente a data
        assertThat(dateOf(3L)).isEqualTo(tomorrow);
        assertThat(timeOf(3L)).isEqualTo(LocalTime.of(10, 0));

        // nulos e lixo -> NULL (best-effort, sem quebrar a migracao)
        assertThat(dateOf(4L)).isNull();
        assertThat(timeOf(4L)).isNull();
        assertThat(dateOf(5L)).isNull();
        assertThat(timeOf(5L)).isNull();
    }

    // ----- helpers -----

    private static String ddmm(LocalDate d) {
        return String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    private void insertLegacy(long id, String date, String time) throws Exception {
        try (Connection c = openConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO appointments (id, customer_phone, appointment_date, appointment_time) "
                                + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, "5511900000000");
            ps.setString(3, date);
            ps.setString(4, time);
            ps.executeUpdate();
        }
    }

    private String columnType(String column) throws Exception {
        try (Connection c = openConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_name = 'appointments' AND column_name = ?")) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private LocalDate dateOf(long id) throws Exception {
        try (Connection c = openConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT appointment_date FROM appointments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, LocalDate.class);
            }
        }
    }

    private LocalTime timeOf(long id) throws Exception {
        try (Connection c = openConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT appointment_time FROM appointments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, LocalTime.class);
            }
        }
    }
}
