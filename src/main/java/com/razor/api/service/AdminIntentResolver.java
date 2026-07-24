package com.razor.api.service;

import java.text.Normalizer;
import java.util.List;

/**
 * Entende o que o admin QUER, sem ele precisar decorar o menu.
 *
 * <p>O dono aprende os comandos com o uso e passa a falar natural ("manda um disparo",
 * "quero ver a agenda de hoje", "pausa o cliente"). Este resolvedor mapeia essas frases —
 * e tambem os atalhos numericos do menu — para uma intencao.
 *
 * <p>Funcao pura (sem estado, sem dependencia): facil de testar exaustivamente.
 */
public final class AdminIntentResolver {

    public enum AdminIntent {
        MENU,
        SAUDACAO,
        AGENDA_HOJE,
        AGENDA_SEMANA,
        AVISO,
        PROMOCAO,
        AGENDAR_DISPARO,
        PAUSAR,
        RETOMAR,
        LISTAR_CLIENTES,
        APAGAR_CLIENTE,
        AJUDA,
        /** Financeiro/comissoes/fechamento: vive no painel web (principio 6 do roadmap). */
        PAINEL_WEB,
        DESCONHECIDO
    }

    private AdminIntentResolver() {
    }

    /** minusculo, sem acento e sem pontuacao — "Promoção!" e "promocao" viram a mesma coisa. */
    static String normalize(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean hasAny(String t, String... termos) {
        for (String termo : termos) {
            if (t.contains(termo)) return true;
        }
        return false;
    }

    /** Saudacao curta e pura ("oi", "bom dia") — nao confundir com "bom dia, manda o aviso". */
    private static boolean isGreeting(String t) {
        List<String> exatas = List.of("oi", "ola", "opa", "eae", "e ai", "salve", "fala",
                "bom dia", "boa tarde", "boa noite", "tudo bem", "beleza", "blz", "fala chefe");
        if (exatas.contains(t)) return true;
        // "oi, tudo bem?" — comeca com saudacao e nao pede mais nada
        return t.length() <= 20 && exatas.stream().anyMatch(t::startsWith);
    }

    public static AdminIntent resolve(String rawText) {
        String t = normalize(rawText);
        if (t.isEmpty()) return AdminIntent.DESCONHECIDO;

        // 1) Atalhos numericos do menu (o dono que ja decorou continua rapido)
        switch (t) {
            case "1": return AdminIntent.AGENDA_HOJE;
            case "2": return AdminIntent.AGENDA_SEMANA;
            case "3": return AdminIntent.AVISO;
            case "4": return AdminIntent.PROMOCAO;
            case "5": return AdminIntent.AGENDAR_DISPARO;
            case "6": return AdminIntent.PAUSAR;
            case "7": return AdminIntent.RETOMAR;
            case "8": return AdminIntent.LISTAR_CLIENTES;
            case "9": return AdminIntent.APAGAR_CLIENTE;
            case "10": return AdminIntent.AJUDA;
            default: break;
        }

        if (hasAny(t, "menu", "comando", "opcoes", "opcao")) return AdminIntent.MENU;
        if (isGreeting(t)) return AdminIntent.SAUDACAO;

        // 2) Financeiro -> painel (antes de "agenda", para "fechamento do caixa" nao virar agenda)
        if (hasAny(t, "financeiro", "faturamento", "caixa", "fechamento", "comiss", "dre",
                "resumo do dia", "resumo da semana", "quanto faturei", "lucro")) {
            return AdminIntent.PAINEL_WEB;
        }

        // 3) Disparos — o mais especifico primeiro ("agendar disparo" contem "disparo")
        boolean falaDeDisparo = hasAny(t, "disparo", "disparar", "mandar mensagem", "enviar mensagem",
                "mensagem para todos", "mensagem pra todos", "avisar todo mundo");
        if (falaDeDisparo && hasAny(t, "agendar", "agenda ", "programar", "marcar", "depois",
                "amanha", "mais tarde", "horario")) {
            return AdminIntent.AGENDAR_DISPARO;
        }
        if (hasAny(t, "promocao", "promo", "oferta", "desconto", "prospec")) return AdminIntent.PROMOCAO;
        if (falaDeDisparo || hasAny(t, "aviso", "avisar", "comunicado", "recado")) return AdminIntent.AVISO;

        // 4) Controle do bot por cliente
        if (hasAny(t, "pausar", "pausa", "silenciar", "calar", "desligar o bot", "parar o bot")) {
            return AdminIntent.PAUSAR;
        }
        if (hasAny(t, "retomar", "retoma", "voltar o bot", "religar", "reativar")) return AdminIntent.RETOMAR;

        // 5) Agenda (operacao)
        if (hasAny(t, "semana")) return AdminIntent.AGENDA_SEMANA;
        if (hasAny(t, "agenda", "agendamento", "horarios de hoje", "quem vem", "quem tem hoje",
                "clientes de hoje", "painel do dia")) {
            return AdminIntent.AGENDA_HOJE;
        }

        // 6) Base de clientes
        if (hasAny(t, "apagar", "apaga", "excluir", "exclui", "deletar", "remover cliente")) {
            return AdminIntent.APAGAR_CLIENTE;
        }
        if (hasAny(t, "listar", "lista", "base de clientes", "meus clientes", "contatos")) {
            return AdminIntent.LISTAR_CLIENTES;
        }

        if (hasAny(t, "ajuda", "manual", "como funciona", "socorro", "duvida")) return AdminIntent.AJUDA;

        return AdminIntent.DESCONHECIDO;
    }

    /** Texto transcrito de audio e um comando (e nao um ditado livre)? */
    public static boolean isCommand(String rawText) {
        AdminIntent intent = resolve(rawText);
        return intent != AdminIntent.DESCONHECIDO && intent != AdminIntent.SAUDACAO;
    }
}
