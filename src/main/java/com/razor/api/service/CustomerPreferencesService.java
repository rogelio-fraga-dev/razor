package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * O que o cliente diz sobre si na conversa (V47), tratado antes da IA:
 *
 * <ul>
 *   <li><b>Opt-out</b>: "parar"/"sair" (e variacoes) — nunca mais recebe mensagem automatica
 *       (campanhas, disparos, pedido de avaliacao). Gravado no cadastro. "quero receber" desfaz,
 *       so a pedido do proprio cliente.</li>
 *   <li><b>Aniversario</b>: "meu aniversário é 12/03", "nasci em 12/03/1990" — grava a data. O bot
 *       pergunta uma vez, no fim do agendamento, se a campanha de aniversario estiver ligada.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPreferencesService {

    /** Ano gravado quando o cliente informa so dia e mes. */
    public static final int UNKNOWN_YEAR = 2000;

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final AutomationSettingsService automationSettingsService;

    private static final Set<String> OPT_OUT = Set.of("parar", "pare", "sair", "stop", "descadastrar",
            "parar mensagens", "pare de mandar", "nao quero receber", "nao quero mais receber",
            "nao me mande mais", "nao mande mais", "parar de receber", "cancelar mensagens", "sair da lista");
    private static final Set<String> OPT_IN = Set.of("quero receber", "voltar a receber", "quero receber mensagens",
            "quero receber promocoes", "receber promocoes");

    private static final Pattern BIRTHDAY = Pattern.compile(
            "(?:aniversario|aniver|niver|nasci)\\D{0,25}?(\\d{1,2})\\s*/\\s*(\\d{1,2})(?:\\s*/\\s*(\\d{2,4}))?");
    private static final Pattern BOOKING_INTENT = Pattern.compile(
            "\\b(marcar|marca|agendar|agenda|reservar|horario|hora|vaga|encaixe|corte|cortar|barba)\\b"
                    + "|\\b\\d{1,2}\\s*h\\b|\\b\\d{1,2}\\s*:\\s*\\d{2}\\b|\\bas \\d");

    /** Responde ao "parar"/"quero receber"; vazio = nao era isso. */
    public Optional<String> handleOptOut(Customer customer, String message) {
        String t = normalize(message);
        if (OPT_OUT.contains(t)) {
            customerService.optOutOfMarketing(customer.getPhoneNumber());
            return Optional.of("Pronto! ✅ Você não vai mais receber mensagens automáticas (promoções, lembretes de "
                    + "retorno e pedidos de avaliação).\n\nSeus agendamentos continuam normais, e se quiser marcar é só "
                    + "chamar aqui. Para voltar a receber, mande *quero receber*.");
        }
        if (OPT_IN.contains(t)) {
            customerService.optInMarketing(customer.getPhoneNumber());
            return Optional.of("Combinado! 😊 Você volta a receber nossas novidades. Para parar, é só mandar *parar*.");
        }
        return Optional.empty();
    }

    /** Grava o aniversario quando a mensagem informa um; devolve a resposta. */
    public Optional<String> handleBirthday(Customer customer, String message) {
        Optional<LocalDate> data = parseBirthday(message, LocalDate.now());
        if (data.isEmpty()) return Optional.empty();
        customer.setBirthDate(data.get());
        customerRepository.save(customer);
        log.info("[ANIVERSARIO] {} informou o aniversario {}", customer.getPhoneNumber(), data.get());
        return Optional.of("Anotado! 🎂 Seu aniversário é dia *" + String.format("%02d/%02d",
                data.get().getDayOfMonth(), data.get().getMonthValue()) + "* — no dia tem um presente te esperando. 😉");
    }

    /**
     * "meu aniversario e 12/03", "niver dia 5/7", "nasci em 12/03/1990". Data impossivel (31/02) ou
     * ano no futuro nao valem.
     */
    static Optional<LocalDate> parseBirthday(String message, LocalDate today) {
        String t = normalize(message);
        // "quero marcar pro meu aniversario dia 12/03 as 10h" e um pedido de horario, nao o cadastro
        // da data: segue para a IA (que agenda) em vez de ser engolido aqui.
        if (t.length() > 80 || BOOKING_INTENT.matcher(t).find()) return Optional.empty();
        Matcher m = BIRTHDAY.matcher(t);
        if (!m.find()) return Optional.empty();
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            int ano = UNKNOWN_YEAR;
            if (m.group(3) != null) {
                ano = Integer.parseInt(m.group(3));
                if (ano < 100) ano += ano > today.getYear() % 100 ? 1900 : 2000;
                if (ano < 1900 || ano > today.getYear()) return Optional.empty();
            }
            MonthDay.of(mes, dia); // valida (29/02 passa: 2000 e bissexto)
            return Optional.of(LocalDate.of(ano, mes, dia));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Pergunta do aniversario para pendurar no fim do agendamento: so com a campanha ligada, sem
     * data no cadastro e uma vez por cliente. Vazio = nao perguntar.
     */
    public String birthdayQuestion(Customer customer) {
        if (customer == null || customer.getBirthDate() != null || customer.getBirthdayAskedAt() != null) return "";
        try {
            if (!automationSettingsService.get().isBirthdayEnabled()) return "";
        } catch (Exception e) {
            return "";
        }
        customer.setBirthdayAskedAt(LocalDateTime.now());
        customerRepository.save(customer);
        return "\n\n🎂 _Ah, e quando é seu aniversário? Me manda assim: *aniversário 12/03* — no dia tem presente!_";
    }

    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9/ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
