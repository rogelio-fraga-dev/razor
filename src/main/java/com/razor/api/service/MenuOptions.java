package com.razor.api.service;

import com.razor.api.client.EvolutionClient;
import com.razor.api.config.RazorProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MenuOptions {

    public static final String ROW_ID_AGENDAR = "menu_agendar";
    public static final String ROW_ID_MEUS_AGENDAMENTOS = "menu_meus_agendamentos";
    public static final String ROW_ID_HORARIOS_LIVRES = "menu_horarios_livres";
    public static final String ROW_ID_ENDERECO = "menu_endereco";
    public static final String ROW_ID_SERVICOS = "menu_servicos";
    public static final String ROW_ID_FIDELIDADE = "menu_fidelidade";
    public static final String ROW_ID_INSTAGRAM = "menu_instagram";
    public static final String ROW_ID_ATENDENTE = "menu_atendente";

    private static final String[] ALL_ROW_IDS = {
            ROW_ID_AGENDAR, ROW_ID_MEUS_AGENDAMENTOS, ROW_ID_HORARIOS_LIVRES,
            ROW_ID_ENDERECO, ROW_ID_SERVICOS, ROW_ID_FIDELIDADE,
            ROW_ID_INSTAGRAM, ROW_ID_ATENDENTE
    };

    private static final String[] ROW_IDS_BY_NUMBER = {
            ROW_ID_AGENDAR, ROW_ID_MEUS_AGENDAMENTOS, ROW_ID_HORARIOS_LIVRES,
            ROW_ID_ENDERECO, ROW_ID_SERVICOS, ROW_ID_FIDELIDADE,
            ROW_ID_INSTAGRAM, ROW_ID_ATENDENTE
    };

    public static String resolveMenuOptionId(String messageText) {
        if (messageText == null) return null;
        String t = messageText.trim();
        for (String id : ALL_ROW_IDS) {
            if (id.equals(t)) return id;
        }
        if (t.length() == 1 && t.charAt(0) >= '1' && t.charAt(0) <= '8') {
            return ROW_IDS_BY_NUMBER[t.charAt(0) - '1'];
        }
        return null;
    }

    public static boolean isAskingForMenu(String messageText) {
        if (messageText == null) return false;
        String t = messageText.trim().toLowerCase();
        return t.equals("menu") || t.equals("opções") || t.equals("opcoes")
                || t.startsWith("ver op") || t.contains("tabela") || t.contains("preço");
    }

    /**
     * Monta a LISTA INTERATIVA (Botões nativos do WhatsApp)
     */
    public static List<Map<String, Object>> buildListSections() {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(EvolutionClient.listRow(ROW_ID_AGENDAR, "📅 Agendar Horário", "Iniciar agendamento por IA"));
        rows.add(EvolutionClient.listRow(ROW_ID_MEUS_AGENDAMENTOS, "📅 Meus Agendamentos", "Ver ou cancelar seu horário"));
        rows.add(EvolutionClient.listRow(ROW_ID_HORARIOS_LIVRES, "🕒 Horários Disponíveis", "Ver as vagas livres"));
        rows.add(EvolutionClient.listRow(ROW_ID_ENDERECO, "📍 Endereço", "Localização e Mapa"));
        rows.add(EvolutionClient.listRow(ROW_ID_SERVICOS, "💰 Serviços e Planos", "Tabela de preços VIP"));
        rows.add(EvolutionClient.listRow(ROW_ID_FIDELIDADE, "🎫 Cartão Fidelidade", "Veja seus pontos e prêmios"));
        rows.add(EvolutionClient.listRow(ROW_ID_INSTAGRAM, "📸 Instagram", "Nossas redes"));
        rows.add(EvolutionClient.listRow(ROW_ID_ATENDENTE, "🗣️ Falar com Barbeiro", "Atendimento humano"));

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(EvolutionClient.listSection("Escolha uma opção", rows));
        return sections;
    }

    public static String getResponseForOption(String rowId, RazorProperties properties) {
        RazorProperties.Menu menu = properties.getMenu();

        switch (rowId) {
            case ROW_ID_ENDERECO:
                return menu.getAddressText() + "\n\n🗺️ *Abrir no Maps:* " + menu.getAddressMapsUrl();
            
            case ROW_ID_SERVICOS:
                return menu.getServicesText() + "\n👉 " + menu.getScheduleUrl();
            
            case ROW_ID_INSTAGRAM:
                return "📸 *Siga nossa Barbearia!*\n\nFique por dentro dos cortes e novidades:\n" + menu.getInstagramUrl();
            
            default:
                return "Opção processada dinamicamente pelo assistente.";
        }
    }

    public static String getMenuAsText(String barbershopName, int points) {
        return getMenuAsText(barbershopName, points, true);
    }

    /** Com {@code loyalty} falso (plano sem cartão fidelidade, como o piloto) a opção 6 aparece como "em breve". */
    public static String getMenuAsText(String barbershopName, int points, boolean loyalty) {
        return "💈 *Menu " + barbershopName + "*\n\n" +
               "1️⃣ Agendar Horário 📅\n" +
               "2️⃣ Meus Agendamentos (Ver/Cancelar)\n" +
               "3️⃣ Horários Disponíveis 🕒\n" +
               "4️⃣ Endereço e Localização 📍\n" +
               "5️⃣ Serviços e Preços (Planos VIP) 💰\n" +
               (loyalty ? "6️⃣ Cartão Fidelidade Digital 🎫 (Você tem " + points + " pontos)\n"
                        : "6️⃣ Cartão Fidelidade 🎫 (em breve)\n") +
               "7️⃣ Instagram 📸\n" +
               "8️⃣ Falar com o Barbeiro 🗣️\n\n" +
               "_Digite o número da opção desejada ou clique no botão abaixo._\n\n" +
               "🎙️ _Dica: Eu também escuto áudios! Fique à vontade para mandar mensagem de voz._";
    }

    public static final String ROW_ID_ADMIN_DASHBOARD = "admin_dashboard";
    public static final String ROW_ID_ADMIN_RESUMO_SEMANA = "admin_resumo_semana";
    public static final String ROW_ID_ADMIN_AGENDA = "admin_agenda";
    public static final String ROW_ID_ADMIN_AGENDA_SEMANA = "admin_agenda_semana";
    public static final String ROW_ID_ADMIN_LISTAR = "admin_listar_clientes";
    public static final String ROW_ID_ADMIN_AVISOS = "admin_avisos";
    public static final String ROW_ID_ADMIN_PROMO = "admin_promo";
    public static final String ROW_ID_ADMIN_PAUSAR = "admin_pausar";
    public static final String ROW_ID_ADMIN_RETOMAR = "admin_retomar";
    public static final String ROW_ID_ADMIN_REL_MATINAL = "admin_rel_matinal";
    public static final String ROW_ID_ADMIN_REL_NOTURNO = "admin_rel_noturno";
    public static final String ROW_ID_ADMIN_APAGAR = "admin_apagar";

    public static final String[] ALL_ADMIN_ROW_IDS = {
        ROW_ID_ADMIN_DASHBOARD, ROW_ID_ADMIN_RESUMO_SEMANA, ROW_ID_ADMIN_AGENDA, ROW_ID_ADMIN_AGENDA_SEMANA,
        ROW_ID_ADMIN_REL_MATINAL, ROW_ID_ADMIN_REL_NOTURNO,
        ROW_ID_ADMIN_LISTAR, ROW_ID_ADMIN_AVISOS,
        ROW_ID_ADMIN_PROMO, ROW_ID_ADMIN_PAUSAR,
        ROW_ID_ADMIN_RETOMAR, ROW_ID_ADMIN_APAGAR
    };

    public static String resolveAdminMenuOptionId(String messageText) {
        if (messageText == null) return null;
        String t = messageText.trim();
        for (String id : ALL_ADMIN_ROW_IDS) {
            if (id.equals(t)) return id;
        }
        return null;
    }

    public static List<Map<String, Object>> buildAdminListSections() {
        List<Map<String, Object>> sections = new ArrayList<>();

        // Seção 1: Gestão Diária
        List<Map<String, String>> gestaoRows = new ArrayList<>();
        gestaoRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_DASHBOARD, "💰 Resumo do Caixa (Hoje)", "Faturamento detalhado de hoje"));
        gestaoRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_AGENDA, "📅 Agenda (Hoje)", "Status de clientes e fila de hoje"));
        gestaoRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_LISTAR, "📋 Listar Clientes", "Ver base de contatos"));
        sections.add(EvolutionClient.listSection("Gestão Diária", gestaoRows));

        // Seção 2: Gestão Semanal
        List<Map<String, String>> gestaoSemanaRows = new ArrayList<>();
        gestaoSemanaRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_RESUMO_SEMANA, "📊 Resumo da Semana", "Faturamento parcial acumulado na semana"));
        gestaoSemanaRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_AGENDA_SEMANA, "📆 Agenda da Semana", "Todos agendamentos programados na semana"));
        sections.add(EvolutionClient.listSection("Gestão Semanal", gestaoSemanaRows));

        // Seção 2: Relatórios do Sistema
        List<Map<String, String>> relRows = new ArrayList<>();
        relRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_REL_MATINAL, "☀️ Relatório Matinal", "Agendamentos programados para hoje"));
        relRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_REL_NOTURNO, "🌙 Relatório Noturno", "Fechamento de cortes e serviços feitos"));
        sections.add(EvolutionClient.listSection("Relatórios do Sistema", relRows));

        // Seção 3: Controle e Disparos
        List<Map<String, String>> controleRows = new ArrayList<>();
        controleRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_AVISOS, "📢 Disparar Avisos", "Enviar aviso geral para toda a base"));
        controleRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_PROMO, "🎯 Promoções do Dia", "Disparar oferta/prospecção de clientes"));
        controleRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_PAUSAR, "⏸️ Pausar Bot", "Silenciar assistente para um cliente"));
        controleRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_RETOMAR, "▶️ Retomar Bot", "Reativar assistente para um cliente"));
        controleRows.add(EvolutionClient.listRow(ROW_ID_ADMIN_APAGAR, "🗑️ Apagar Cliente", "Remover um cliente do banco de dados"));
        sections.add(EvolutionClient.listSection("Controle e Disparos", controleRows));

        return sections;
    }
}