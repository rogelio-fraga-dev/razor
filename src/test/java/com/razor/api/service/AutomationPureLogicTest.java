package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.AutomationSend;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.WaitlistEntry;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regras puras da Parte 4 (V47): modelos, aniversario, desejo da lista de espera, relatorio. */
class AutomationPureLogicTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23); // segunda

    // ------------------------------------------------------------ modelos

    @Test
    @DisplayName("modelo: troca variaveis, sem link vira 'responder aqui', variavel desconhecida fica, rodape sempre")
    void templates() {
        String out = AutomationTemplates.render("Oi {nome}, {dias} dias. {link} {xpto}",
                new java.util.LinkedHashMap<>(Map.of("nome", "Ana", "dias", "30", "link", "")));
        assertThat(out).startsWith("Oi Ana, 30 dias. é só responder aqui").contains("{xpto}")
                .endsWith(AutomationTemplates.OPT_OUT_FOOTER);
        assertThat(AutomationTemplates.effective("  ", AutomationSend.Kind.RETURN)).isEqualTo(AutomationTemplates.DEFAULT_RETURN);
        assertThat(AutomationTemplates.firstName("  Carlos  Souza ")).isEqualTo("Carlos");
        assertThat(AutomationTemplates.firstName(null)).isEqualTo("tudo bem");
    }

    // ------------------------------------------------------------ aniversario

    @Test
    @DisplayName("aniversario: dd/mm, com ano de 2 ou 4 digitos; data impossivel ou futura nao vale")
    void parseBirthday() {
        assertThat(CustomerPreferencesService.parseBirthday("aniversário 12/03", TODAY)).contains(LocalDate.of(2000, 3, 12));
        assertThat(CustomerPreferencesService.parseBirthday("Meu niver é dia 5/7", TODAY)).contains(LocalDate.of(2000, 7, 5));
        assertThat(CustomerPreferencesService.parseBirthday("nasci em 12/03/1990", TODAY)).contains(LocalDate.of(1990, 3, 12));
        assertThat(CustomerPreferencesService.parseBirthday("nasci em 12/03/90", TODAY)).contains(LocalDate.of(1990, 3, 12));
        assertThat(CustomerPreferencesService.parseBirthday("aniversario 29/02", TODAY)).contains(LocalDate.of(2000, 2, 29));
        assertThat(CustomerPreferencesService.parseBirthday("aniversario 31/02", TODAY)).isEmpty();
        assertThat(CustomerPreferencesService.parseBirthday("nasci em 12/03/2030", TODAY)).isEmpty();
        assertThat(CustomerPreferencesService.parseBirthday("quero marcar dia 12/03", TODAY)).isEmpty();
    }

    @Test
    @DisplayName("aniversario dentro de um pedido de horario segue para a IA (nao e engolido)")
    void birthdayInsideBookingRequest_notIntercepted() {
        assertThat(CustomerPreferencesService.parseBirthday("quero marcar pro meu aniversario dia 12/03", TODAY)).isEmpty();
        assertThat(CustomerPreferencesService.parseBirthday("meu aniversario e 12/03, tem horario as 10?", TODAY)).isEmpty();
        assertThat(CustomerPreferencesService.parseBirthday("niver 12/03 queria um corte 15h", TODAY)).isEmpty();
    }

    // ------------------------------------------------------------ desejo da lista de espera

    @Test
    @DisplayName("desejo: data exata, amanha, dia da semana e periodo")
    void wish() {
        // Dia e mês valem; o ano depende de quando o teste roda (data passada vai para o ano seguinte).
        LocalDate d = WaitlistWish.parse("25/11", null, TODAY).date();
        assertThat(d.getDayOfMonth()).isEqualTo(25);
        assertThat(d.getMonthValue()).isEqualTo(11);
        assertThat(WaitlistWish.parse("amanhã à tarde", null, TODAY))
                .isEqualTo(new WaitlistWish(TODAY.plusDays(1), null, WaitlistEntry.Period.TARDE));
        // "amanhã" contem "manha": nao pode virar periodo manha.
        assertThat(WaitlistWish.parse("amanhã", null, TODAY)).isEqualTo(new WaitlistWish(TODAY.plusDays(1), null, null));
        assertThat(WaitlistWish.parse("sexta", "de manhã", TODAY))
                .isEqualTo(new WaitlistWish(null, DayOfWeek.FRIDAY, WaitlistEntry.Period.MANHA));
        assertThat(WaitlistWish.parse("qualquer dia", null, TODAY)).isEqualTo(new WaitlistWish(null, null, null));
        assertThat(WaitlistWish.isAny("Tanto faz")).isTrue();
        assertThat(WaitlistWish.weekdayOf("qualquer")).isEmpty();
    }

    // ------------------------------------------------------------ lista de espera: casamento e resposta

    private static WaitlistEntry entry() {
        return WaitlistEntry.builder().id(UUID.randomUUID()).customerPhone("5511988887777")
                .preferredDay("qualquer dia").createdAt(TODAY.atStartOfDay()).build();
    }

    @Test
    @DisplayName("vaga x desejo: data exata, dia da semana, periodo, barbeiro e pedido velho")
    void matches() {
        LocalDateTime now = TODAY.atTime(9, 0);
        UUID ze = UUID.randomUUID();
        var sextaManha = new WaitlistOfferService.Slot(LocalDate.of(2026, 11, 27), LocalTime.of(10, 0), "Corte", ze);

        assertThat(WaitlistOfferService.matches(entry(), sextaManha, TODAY, now)).isTrue();

        WaitlistEntry sexta = entry();
        sexta.setPreferredDay("sexta");
        assertThat(WaitlistOfferService.matches(sexta, sextaManha, TODAY, now)).isTrue();
        sexta.setPreferredDay("quinta");
        assertThat(WaitlistOfferService.matches(sexta, sextaManha, TODAY, now)).isFalse();

        WaitlistEntry data = entry();
        data.setPreferredDate(LocalDate.of(2026, 11, 26));
        assertThat(WaitlistOfferService.matches(data, sextaManha, TODAY, now)).isFalse();

        WaitlistEntry tarde = entry();
        tarde.setPreferredPeriod(WaitlistEntry.Period.TARDE);
        assertThat(WaitlistOfferService.matches(tarde, sextaManha, TODAY, now)).isFalse();

        WaitlistEntry outroBarbeiro = entry();
        outroBarbeiro.setProfessionalId(UUID.randomUUID());
        assertThat(WaitlistOfferService.matches(outroBarbeiro, sextaManha, TODAY, now)).isFalse();

        WaitlistEntry velho = entry();
        velho.setCreatedAt(now.minusDays(WaitlistOfferService.STALE_DAYS + 1));
        assertThat(WaitlistOfferService.matches(velho, sextaManha, TODAY, now)).isFalse();

        WaitlistEntry jaMarcou = entry();
        jaMarcou.setStatus(WaitlistEntry.Status.BOOKED);
        assertThat(WaitlistOfferService.matches(jaMarcou, sextaManha, TODAY, now)).isFalse();
    }

    @Test
    @DisplayName("resposta a oferta: sim/nao curtos e inequivocos; '1'/'2' e frases longas seguem a conversa")
    void parseAnswer() {
        assertThat(WaitlistOfferService.parseAnswer("Sim!")).isEqualTo(WaitlistOfferService.Answer.YES);
        assertThat(WaitlistOfferService.parseAnswer("pode marcar")).isEqualTo(WaitlistOfferService.Answer.YES);
        assertThat(WaitlistOfferService.parseAnswer("sim, às 10 está ótimo")).isEqualTo(WaitlistOfferService.Answer.YES);
        assertThat(WaitlistOfferService.parseAnswer("Não")).isEqualTo(WaitlistOfferService.Answer.NO);
        assertThat(WaitlistOfferService.parseAnswer("1")).isNull();
        assertThat(WaitlistOfferService.parseAnswer("quero outro horário")).isNull();
        assertThat(WaitlistOfferService.parseAnswer(null)).isNull();
    }

    @Test
    @DisplayName("mensagem da oferta traz dia, data, hora, servico e prazo")
    void offerMessage() {
        var slot = new WaitlistOfferService.Slot(LocalDate.of(2026, 11, 27), LocalTime.of(10, 30), "Corte", null);
        String msg = WaitlistOfferService.offerMessage("João Silva", slot, "Corte", 15,
                TODAY.atTime(9, 15), TODAY.atTime(9, 0));
        assertThat(msg).contains("Opa, João").contains("Sexta").contains("27/11").contains("10:30")
                .contains("*Corte*").contains("15 min");
    }

    // ------------------------------------------------------------ relatorio semanal

    @Test
    @DisplayName("relatorio: variacao, dia/hora de envio e chave da semana")
    void weeklyReport() {
        assertThat(WeeklyReportService.variacao(new BigDecimal("1200"), new BigDecimal("1000"))).isEqualTo(" (▲ 20%)");
        assertThat(WeeklyReportService.variacao(new BigDecimal("800"), new BigDecimal("1000"))).isEqualTo(" (▼ 20%)");
        assertThat(WeeklyReportService.variacao(new BigDecimal("1000"), new BigDecimal("1001"))).isEqualTo(" (estável)");
        assertThat(WeeklyReportService.variacao(new BigDecimal("1000"), BigDecimal.ZERO)).isEmpty();

        AutomationSettings cfg = AutomationSettings.defaults(UUID.randomUUID()); // segunda, 8h
        assertThat(WeeklyReportService.isDue(cfg, TODAY.atTime(8, 2))).isTrue();
        assertThat(WeeklyReportService.isDue(cfg, TODAY.atTime(15, 0))).isTrue();
        assertThat(WeeklyReportService.isDue(cfg, TODAY.atTime(7, 59))).isFalse();
        assertThat(WeeklyReportService.isDue(cfg, TODAY.plusDays(1).atTime(8, 0))).isFalse();
        cfg.setWeeklyReportEnabled(false);
        assertThat(WeeklyReportService.isDue(cfg, TODAY.atTime(8, 2))).isFalse();

        assertThat(WeeklyReportService.weekKey(TODAY)).isEqualTo("2026-W48");
        assertThat(WeeklyReportService.weekKey(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
    }

    @Test
    @DisplayName("horarios vazios: so dias abertos, dentro do expediente, os 3 menos ocupados")
    void emptiest() {
        var quoter = PricingService.Quoter.of(java.util.List.of(com.razor.api.model.ServiceOffering.builder()
                .id(UUID.randomUUID()).name("Corte").price(new BigDecimal("40")).durationMinutes(60).build()));
        LocalDate amanha = TODAY.plusDays(1); // terca
        var cheio = new java.util.ArrayList<com.razor.api.model.Appointment>();
        for (int h = 9; h < 12; h++) {
            cheio.add(com.razor.api.model.Appointment.builder().appointmentDate(amanha)
                    .appointmentTime(LocalTime.of(h, 0)).service("Corte").build());
        }
        var out = WeeklyReportService.emptiest(cheio, TODAY, java.util.EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                LocalTime.of(9, 0), LocalTime.of(19, 0), 1, quoter);
        assertThat(out).hasSize(3).allSatisfy(o -> assertThat(o.date().getDayOfWeek())
                .isIn(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
        assertThat(out).noneMatch(o -> o.date().equals(amanha) && o.faixa().equals("manhã"));
    }
}
