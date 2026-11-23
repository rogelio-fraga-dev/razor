package com.razor.api.service;

import com.razor.api.model.AutomationSend;
import java.util.Map;

/**
 * Modelos de mensagem das campanhas automaticas (V47) e a troca das variaveis.
 *
 * <p>Variaveis: {@code {nome}} (primeiro nome), {@code {barbearia}}, {@code {link}} (link de
 * agendamento; sem link configurado vira "é só responder aqui") e, por campanha, {@code {dias}},
 * {@code {servico}}, {@code {cupom}}, {@code {desconto}} e {@code {validade}}. Variavel desconhecida
 * fica como esta — o dono ve na previa que digitou errado.
 *
 * <p>Toda mensagem de marketing ganha o rodape de opt-out ({@link #OPT_OUT_FOOTER}), que nao e
 * editavel: sem ele o cliente nao sabe que pode pedir para parar.
 */
public final class AutomationTemplates {

    private AutomationTemplates() {
    }

    public static final String OPT_OUT_FOOTER = "\n\n_Não quer mais receber estas mensagens? Responda *parar*._";

    /** O que entra no lugar de {link} quando a barbearia nao tem link de agendamento. */
    static final String NO_LINK = "é só responder aqui 😉";

    public static final String DEFAULT_LAPSED = """
            Fala, {nome}! 💈

            Faz {dias} dias que você não aparece aqui na {barbearia} — a gente sentiu sua falta! \
            Bora deixar o visual em dia?

            Para marcar: {link}""";

    public static final String DEFAULT_BIRTHDAY = """
            Parabéns, {nome}! 🎉🎂

            A {barbearia} te deseja um ótimo aniversário! Para comemorar, você ganhou *{desconto} de desconto* \
            no próximo atendimento com o cupom *{cupom}* (vale até {validade}).

            Para marcar: {link}""";

    public static final String DEFAULT_RETURN = """
            E aí, {nome}! ✂️

            Já faz {dias} dias do seu último {servico} na {barbearia} — é a hora certa de dar aquele trato \
            antes de perder o desenho.

            Para marcar: {link}""";

    public static String defaultFor(AutomationSend.Kind kind) {
        return switch (kind) {
            case LAPSED -> DEFAULT_LAPSED;
            case BIRTHDAY -> DEFAULT_BIRTHDAY;
            case RETURN -> DEFAULT_RETURN;
            case WEEKLY_REPORT -> "";
        };
    }

    /** O modelo salvo, ou o padrao quando vazio. */
    public static String effective(String template, AutomationSend.Kind kind) {
        return template == null || template.isBlank() ? defaultFor(kind) : template;
    }

    /** Troca as variaveis e acrescenta o rodape de opt-out. */
    public static String render(String template, Map<String, String> vars) {
        String out = template == null ? "" : template;
        for (var e : vars.entrySet()) {
            String value = e.getValue();
            if ("link".equals(e.getKey()) && (value == null || value.isBlank())) value = NO_LINK;
            out = out.replace("{" + e.getKey() + "}", value == null ? "" : value);
        }
        return out.trim() + OPT_OUT_FOOTER;
    }

    /** Primeiro nome para o {nome}; sem nome, um "tudo bem" neutro. */
    public static String firstName(String name) {
        if (name == null || name.isBlank()) return "tudo bem";
        return name.trim().split("\\s+")[0];
    }
}
