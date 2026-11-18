package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Customer;
import com.razor.api.model.Professional;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.ConsoleText;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fluxo do ADMINISTRADOR: menu de gestao, dashboards, disparos (imediatos e
 * agendados), pausa/retomada de clientes, importacao de CSV/agenda e a maquina
 * de estados do admin. Extraido do {@code OrchestratorService} (Fase 0.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommandService {

    // Principio 6 do roadmap: o WhatsApp do admin e OPERACAO rapida (agenda, disparos,
    // pausar/retomar). Financeiro, comissoes e fechamento vivem no PAINEL WEB.
    private static final String PAINEL_REDIRECT = """
            📊 *Isso agora vive no painel!*

            Financeiro, comissões e fechamento de caixa ficaram no painel web — completos e sempre atualizados.

            Aqui no WhatsApp ficam as ações rápidas do dia a dia. Digite *menu* para vê-las.
            """;

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final AgendaService agendaService;
    private final RazorProperties properties;
    private final WaitlistRepository waitlistRepository;
    private final WhatsAppMessenger messenger;
    private final EvolutionMediaService evolutionMediaService;
    private final StateStore stateStore;
    private final BarbershopSettingsService barbershopSettingsService;

    /** Fluxo do admin abandonado no meio expira sozinho. */
    private static final Duration ADMIN_TTL = Duration.ofMinutes(30);

    private void sendMsg(String phone, String text, String role) {
        messenger.send(phone, text, role);
    }

    private String sanitizeForConsole(String text) {
        return ConsoleText.sanitize(text);
    }

    private String fetchBase64FromEvolution(String messageId) {
        return evolutionMediaService.fetchBase64(messageId);
    }

    /** Audio transcrito e um comando (e nao um ditado livre)? Mesma inteligencia do texto. */
    private boolean isSystemCommand(String text) {
        return AdminIntentResolver.isCommand(text);
    }

    // ==========================================
    // FLUXO DO ADMINISTRADOR
    // ==========================================
    public void processAdminMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        String command = null;

        if (webhook.hasDocument()) {
            stateStore.remove(StateKeys.adminState(phoneNumber));
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                log.info("[ADMIN] CSV de contatos recebido do Admin {}.", phoneNumber);
                sendMsg(phoneNumber, "⏳ Lendo a base CSV...", "ADMIN");
                try {
                    int salvos = customerService.importCustomersFromCsvBase64(base64);
                    log.info("[ADMIN] CSV processado com sucesso. {} contatos salvos.", salvos);
                    sendMsg(phoneNumber, "✅ Importação Concluída! *" + salvos + "* clientes na base.", "ADMIN");
                } catch (Exception e) {
                    log.error("[ADMIN] Falha ao processar CSV: {}", e.getMessage(), e);
                    sendMsg(phoneNumber, "❌ Erro no CSV.", "ADMIN");
                }
            } else {
                log.warn("[ADMIN] Recebido CSV corrompido ou sem base64.");
                sendMsg(phoneNumber, "❌ O arquivo chegou corrompido.", "ADMIN");
            }
            return;
        }

        if (webhook.hasImage()) {
            stateStore.remove(StateKeys.adminState(phoneNumber));
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                log.info("[ADMIN] Imagem de agenda recebida do Admin {} para processamento via GPT-4o Vision.", phoneNumber);
                sendMsg(phoneNumber, "⏳ Lendo imagem da agenda com IA...", "ADMIN");
                try {
                    String agendaJson = openAIService.extractAgendaFromImage(base64, webhook.getMimeType());
                    log.info("[ADMIN] JSON extraido da imagem de agenda: {}", sanitizeForConsole(agendaJson));
                    int salvos = agendaService.processAgenda(agendaJson);
                    log.info("[ADMIN] Agenda atualizada. {} agendamentos importados.", salvos);
                    sendMsg(phoneNumber, "✅ " + salvos + " clientes identificados na imagem!", "ADMIN");
                } catch (Exception e) {
                    log.error("[ADMIN] Falha ao ler imagem com Vision: {}", e.getMessage(), e);
                    sendMsg(phoneNumber, "❌ Erro na Visão IA.", "ADMIN");
                }
            } else {
                log.warn("[ADMIN] Recebida imagem da agenda corrompida.");
                sendMsg(phoneNumber, "❌ A imagem não pôde ser decodificada.", "ADMIN");
            }
            return;
        }

        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                log.warn("[ADMIN] Audio recebido do Admin, mas sem base64 disponível.");
                return;
            }
            log.info("[ADMIN] Mensagem de voz recebida do Admin {}, transcrevendo com Whisper...", phoneNumber);
            sendMsg(phoneNumber, "🎧 Ouvindo áudio...", "ADMIN");
            command = openAIService.transcribeAudio(base64, webhook.getMimeType());
            if (command == null) {
                log.warn("[ADMIN] Falha ao transcrever audio do Admin {}", phoneNumber);
                sendMsg(phoneNumber, "❌ Não consegui transcrever o áudio. Tente de novo ou mande por texto.", "ADMIN");
                return;
            }
            log.info("[ADMIN] Audio transcrito: '{}'", sanitizeForConsole(command));

            boolean semEstadoAtivo = stateStore.get(StateKeys.adminState(phoneNumber), String.class).isEmpty();
            if (semEstadoAtivo && command != null && !isSystemCommand(command)) {
                sendMsg(phoneNumber, "📝 *Transcrição Livre:*\n" + command, "ADMIN");
                return;
            }
        } else {
            command = webhook.getMessageText();
            log.info("[ADMIN] Comando de texto recebido do Admin: '{}'", sanitizeForConsole(command));
        }

        if (command != null) {
            String originalCommand = command.trim();
            // Normalizacao unica, a mesma do resolvedor de intencao.
            String cmdLower = AdminIntentResolver.normalize(originalCommand);

            String adminRowId = MenuOptions.resolveAdminMenuOptionId(originalCommand);
            if (adminRowId != null) {
                if (MenuOptions.ROW_ID_ADMIN_DASHBOARD.equals(adminRowId)) cmdLower = "1";
                else if (MenuOptions.ROW_ID_ADMIN_AVISOS.equals(adminRowId)) cmdLower = "2";
                else if (MenuOptions.ROW_ID_ADMIN_PROMO.equals(adminRowId)) cmdLower = "3";
                else if (MenuOptions.ROW_ID_ADMIN_PAUSAR.equals(adminRowId)) cmdLower = "4";
                else if (MenuOptions.ROW_ID_ADMIN_RETOMAR.equals(adminRowId)) cmdLower = "5";
                else if (MenuOptions.ROW_ID_ADMIN_AGENDA.equals(adminRowId)) cmdLower = "6";
                else if (MenuOptions.ROW_ID_ADMIN_REL_MATINAL.equals(adminRowId)) cmdLower = "7";
                else if (MenuOptions.ROW_ID_ADMIN_REL_NOTURNO.equals(adminRowId)) cmdLower = "8";
                else if (MenuOptions.ROW_ID_ADMIN_LISTAR.equals(adminRowId)) cmdLower = "9";
                else if (MenuOptions.ROW_ID_ADMIN_APAGAR.equals(adminRowId)) cmdLower = "10";
                else if (MenuOptions.ROW_ID_ADMIN_RESUMO_SEMANA.equals(adminRowId)) cmdLower = "12";
                else if (MenuOptions.ROW_ID_ADMIN_AGENDA_SEMANA.equals(adminRowId)) cmdLower = "13";
            }

            String currentState = stateStore.get(StateKeys.adminState(phoneNumber), String.class).orElse(null);

            if (currentState != null) {
                if (cmdLower.equals("cancelar")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    stateStore.getList(StateKeys.adminResumeOptions(phoneNumber), String.class).orElse(null);
                    stateStore.getList(StateKeys.adminDeleteOptions(phoneNumber), String.class).orElse(null);
                    stateStore.get(StateKeys.adminScheduledMsg(phoneNumber), String.class).orElse(null);
                    sendFullMenu(phoneNumber);
                    return;
                }
                if (currentState.equals("AVISO_BASE")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    performBroadcast(phoneNumber, originalCommand, false);
                    return;
                }
                if (currentState.equals("AVISO_PROSPEC")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    performBroadcast(phoneNumber, originalCommand, true);
                    return;
                }
                if (currentState.equals("PAUSAR")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    String target = cmdLower.replaceAll("[^0-9]", "");
                    customerService.pauseCustomer(target, 60);
                    sendMsg(phoneNumber, "⏸️ Robô silenciado com sucesso para esse cliente.", "ADMIN");
                    return;
                }
                if (currentState.equals("RETOMAR")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    List<String> options = stateStore.getList(StateKeys.adminResumeOptions(phoneNumber), String.class).orElse(null);
                    try {
                        int index = Integer.parseInt(cmdLower.replaceAll("[^0-9]", "")) - 1;
                        if (options != null && index >= 0 && index < options.size()) {
                            String targetPhone = options.get(index);
                            customerService.resumeCustomer(targetPhone);
                            sendMsg(phoneNumber, "▶️ Robô religado com sucesso.", "ADMIN");
                        } else {
                            sendMsg(phoneNumber, "❌ Opção inválida.", "ADMIN");
                        }
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Formato inválido.", "ADMIN");
                    }
                    return;
                }
                if (currentState.equals("APAGAR_CONTATO")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    List<String> options = stateStore.getList(StateKeys.adminDeleteOptions(phoneNumber), String.class).orElse(null);
                    try {
                        int index = Integer.parseInt(cmdLower.replaceAll("[^0-9]", "")) - 1;
                        if (options != null && index >= 0 && index < options.size()) {
                            String targetPhone = options.get(index);
                            Customer c = customerRepository
                                    .findByBarbershopIdAndPhoneNumber(TenantContext.getCurrent(), targetPhone).orElse(null);
                            if (c != null) {
                                interactionRepository.deleteByCustomerId(c.getId());
                                customerRepository.delete(c);
                                sendMsg(phoneNumber, "✅ Cliente apagado da base!", "ADMIN");
                            } else {
                                sendMsg(phoneNumber, "❌ Cliente não encontrado.", "ADMIN");
                            }
                        } else {
                            sendMsg(phoneNumber, "❌ Opção inválida.", "ADMIN");
                        }
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Erro ao apagar.", "ADMIN");
                    }
                    return;
                }
                if (currentState.equals("AGENDAR_DISPARO_MSG")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    stateStore.put(StateKeys.adminScheduledMsg(phoneNumber), originalCommand, ADMIN_TTL);
                    stateStore.put(StateKeys.adminState(phoneNumber), "AGENDAR_DISPARO_DATA", ADMIN_TTL);
                    sendMsg(phoneNumber, "🗓️ *Data e Hora:*\nDigite o horário do disparo no formato numérico: *DD/MM HH:MM* (Ex: 28/05 10:30)", "ADMIN");
                    return;
                }
                if (currentState.equals("AGENDAR_DISPARO_DATA")) {
                    stateStore.remove(StateKeys.adminState(phoneNumber));
                    String msg = stateStore.get(StateKeys.adminScheduledMsg(phoneNumber), String.class).orElse(null);
                    try {
                        String[] parts = cmdLower.replaceAll("[^0-9/:]", " ").trim().split("\\s+");
                        String datePart = parts[0];
                        String timePart = parts[1];
                        int day = Integer.parseInt(datePart.split("/")[0]);
                        int month = Integer.parseInt(datePart.split("/")[1]);
                        int hour = Integer.parseInt(timePart.split(":")[0]);
                        int minute = Integer.parseInt(timePart.split(":")[1]);
                        LocalDateTime execTime = LocalDateTime.of(LocalDateTime.now().getYear(), month, day, hour, minute);
                        if (execTime.isBefore(LocalDateTime.now())) execTime = execTime.plusYears(1);

                        agendaService.agendarDisparoMassa(msg, execTime);
                        sendMsg(phoneNumber, "✅ Disparo agendado com sucesso para " + datePart + " às " + timePart + "!", "ADMIN");
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Formato inválido. Use *DD/MM HH:MM* (Ex: 28/05 10:30).", "ADMIN");
                    }
                    return;
                }
            }

            // O admin NAO precisa decorar o menu: a frase natural vira intencao.
            AdminIntentResolver.AdminIntent intent = AdminIntentResolver.resolve(cmdLower);

            if (intent == AdminIntentResolver.AdminIntent.MENU) {
                sendFullMenu(phoneNumber);
            } else if (intent == AdminIntentResolver.AdminIntent.SAUDACAO) {
                sendGreeting(phoneNumber);
            } else if (intent == AdminIntentResolver.AdminIntent.PAINEL_WEB) {
                // Relatorios e configuracao financeira vivem no painel web (principio 6 do roadmap).
                sendMsg(phoneNumber, PAINEL_REDIRECT, "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.AGENDA_HOJE) {
                long total = customerRepository.countByBarbershopId(TenantContext.getCurrent());
                long waitlistCount = waitlistRepository.countByBarbershopId(TenantContext.getCurrent());
                long pausedCount = customerService.getPausedPhones().size();
                String painelAgendamentos = agendaService.getPainelAgendamentosDetalhado(total, waitlistCount, pausedCount);
                sendMsg(phoneNumber, painelAgendamentos, "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.AGENDA_SEMANA) {
                String agendaSemana = agendaService.getAgendaSemanaText();
                sendMsg(phoneNumber, agendaSemana, "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.AVISO) {
                stateStore.put(StateKeys.adminState(phoneNumber), "AVISO_BASE", ADMIN_TTL);
                sendMsg(phoneNumber, "📢 *Disparo de Avisos*\nEnvie a mensagem agora. _(Ou digite 'cancelar')_", "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.PROMOCAO) {
                stateStore.put(StateKeys.adminState(phoneNumber), "AVISO_PROSPEC", ADMIN_TTL);
                sendMsg(phoneNumber, "🎯 *Promoções do dia*\nEnvie a oferta que deseja disparar para a base toda.", "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.AGENDAR_DISPARO) {
                stateStore.put(StateKeys.adminState(phoneNumber), "AGENDAR_DISPARO_MSG", ADMIN_TTL);
                sendMsg(phoneNumber, "📝 *Agendar Disparo*\nQual é a mensagem que deseja enviar para a base? (Digite 'cancelar' para abortar)", "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.PAUSAR) {
                stateStore.put(StateKeys.adminState(phoneNumber), "PAUSAR", ADMIN_TTL);
                sendMsg(phoneNumber, "⏸️ *Pausar Robô*\nDigite o telefone com DDD do cliente. _(Ou digite 'cancelar')_", "ADMIN");
            } else if (intent == AdminIntentResolver.AdminIntent.RETOMAR) {
                List<String> pausedPhones = customerService.getPausedPhones();
                if (pausedPhones.isEmpty()) {
                    sendMsg(phoneNumber, "▶️ *Retomar*\nNenhum cliente pausado no momento.", "ADMIN");
                } else {
                    stateStore.put(StateKeys.adminState(phoneNumber), "RETOMAR", ADMIN_TTL);
                    stateStore.put(StateKeys.adminResumeOptions(phoneNumber), pausedPhones, ADMIN_TTL);
                    StringBuilder sb = new StringBuilder("▶️ *Retomar Robô - Clientes Pausados*\n\n");
                    for (int i = 0; i < pausedPhones.size(); i++) {
                        sb.append("*").append(i + 1).append("* - ").append(pausedPhones.get(i)).append("\n");
                    }
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* _(Ou digite 'cancelar')_.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (intent == AdminIntentResolver.AdminIntent.LISTAR_CLIENTES) {
                List<Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
                if (customers.isEmpty()) {
                    sendMsg(phoneNumber, "📭 Nenhum cliente salvo na base.", "ADMIN");
                } else {
                    StringBuilder sb = new StringBuilder("📋 *Base (" + customers.size() + ")*\n\n");
                    for (Customer c : customers) sb.append("👤 ").append(c.getName()).append(" - ").append(c.getPhoneNumber()).append("\n");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (intent == AdminIntentResolver.AdminIntent.APAGAR_CLIENTE) {
                List<Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
                if (customers.isEmpty()) {
                    sendMsg(phoneNumber, "📭 Nenhum cliente salvo.", "ADMIN");
                } else {
                    stateStore.put(StateKeys.adminState(phoneNumber), "APAGAR_CONTATO", ADMIN_TTL);
                    List<String> phoneList = new ArrayList<>();
                    StringBuilder sb = new StringBuilder("🗑️ *Apagar Cliente*\n\n");
                    for (int i = 0; i < customers.size(); i++) {
                        phoneList.add(customers.get(i).getPhoneNumber());
                        sb.append("*").append(i + 1).append("* - ").append(customers.get(i).getName()).append("\n");
                    }
                    stateStore.put(StateKeys.adminDeleteOptions(phoneNumber), phoneList, ADMIN_TTL);
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* para apagar _(Ou digite 'cancelar')_.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (intent == AdminIntentResolver.AdminIntent.AJUDA) {
                String manual = """
                        📖 *MANUAL DO ADMINISTRADOR - RAZOR*

                        Aqui estão todas as automações que o sistema realiza nos bastidores:

                        🤖 *Atendimento e IA:*
                        - A IA agenda, tira dúvidas e negocia horários sozinha.
                        - Protege a agenda (não marca 2 clientes na mesma hora).
                        - Lê fotos de agenda em papel se você enviar para cá.

                        ⏰ *Rotinas Automáticas (Horário de Brasília):*
                        - *08:00* ➡️ Relatório Matinal com os clientes de hoje.
                        - *11:00* ➡️ Cobra clientes sumidos (mais de 15 dias sem vir).
                        - *21:00* ➡️ Fechamento de Caixa com faturamento.
                        - *1 Hora Antes* ➡️ Lembrete de agendamento pro cliente.
                        - *1 Hora Depois* ➡️ Pesquisa de satisfação pro cliente.

                        ⭐ *Fidelidade e Retenção:*
                        - O cliente ganha 1 ponto a cada novo agendamento realizado.
                        - Aos 10 pontos, ganha cupom automático de 10% OFF.
                        - *Sextou:* Envia lembrete toda sexta-feira às 09h para TODOS os usuários sem agendamento no fim de semana.

                        🛠️ *Disparos e Controle:*
                        - O botão de 'Avisos' (3) e 'Promoções' (4) dispara mensagem IMEDIATA para todos.
                        - O botão 'Agendar Disparo' (5) permite escrever um texto para disparar num dia e hora futuros.
                        - Pressione 10 ou diga 'manual' para ver esta mensagem novamente.

                        📊 *Gestão (painel web):*
                        - Financeiro, comissões, fechamento, clube de assinatura e configurações do bot ficam no painel — acesse pelo navegador.
                        """;
                sendMsg(phoneNumber, manual, "ADMIN");
            } else {
                // Nao entendeu: ENSINA pelo exemplo, em vez de mandar "digite menu".
                if (!webhook.hasAudio()) {
                    sendMsg(phoneNumber, """
                            Não peguei essa 🤔

                            Pode falar normal, tipo:
                            • _"agenda de hoje"_
                            • _"manda um aviso"_ ou _"disparar promoção"_
                            • _"pausar cliente"_
                            • _"listar clientes"_

                            _(ou *menu* para ver tudo)_""", "ADMIN");
                }
            }
        }
    }

    /**
     * Resposta a um "oi": em vez de mandar abrir o menu, ja entrega a informacao mais util do
     * momento (o dia) e ensina 3 comandos naturais — e assim que o dono aprende sem decorar nada.
     */
    private void sendGreeting(String phoneNumber) {
        int hoje = agendaService.getAgendamentosHojeCount();
        String resumo = hoje == 0
                ? "📅 Nenhum corte marcado para hoje ainda."
                : "📅 Hoje você tem *" + hoje + (hoje == 1 ? " corte* marcado." : " cortes* marcados.");

        sendMsg(phoneNumber, "Fala, chefe! 👊\n\n" + resumo + """


                É só me dizer o que precisa:
                • _"agenda de hoje"_
                • _"disparar promoção"_
                • _"pausar cliente"_

                _(ou *menu* para ver todas as opções)_""", "ADMIN");
    }

    private void sendFullMenu(String phoneNumber) {
        log.info("[SISTEMA] Enviando Menu Textual de Gestao para o Administrador: {}", phoneNumber);
        String fullMenu = """
                🛠️ *PAINEL DA BARBEARIA* — ações rápidas

                *1* - 📅 Agenda do Dia
                *2* - 📆 Agenda da Semana
                *3* - 📢 Avisos (Geral)
                *4* - 🎯 Promoções do dia
                *5* - 📝 Agendar Disparo
                *6* - ⏸️ Pausar bot (Cliente)
                *7* - ▶️ Retomar bot (Cliente)
                *8* - 📋 Listar Clientes
                *9* - 🗑️ Apagar um Cliente
                *10* - ℹ️ Ajuda / Manual

                📊 _Financeiro, comissões e fechamento: no *painel web*._

                _Digite o número ou comando desejado._
                """;
        sendMsg(phoneNumber, fullMenu, "ADMIN");
    }

    @Async
    public void performBroadcast(String adminPhone, String message, boolean isProspecting) {
        List<Customer> allCustomers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
        sendMsg(adminPhone, "🚀 Iniciando disparo para " + allCustomers.size() + " contatos...", "ADMIN");

        // Nome resolvido uma vez, fora do laco: o disparo percorre a base inteira e nao ha
        // motivo para reler as configuracoes do tenant a cada contato.
        String barbershopName = barbershopSettingsService.resolve().displayName();

        List<String> contatosSucesso = new ArrayList<>();
        int falhas = 0;
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone)) continue;
                String header = isProspecting
                        ? "💈 *" + barbershopName + " - Promoção* 💈\n\n"
                        : "📢 *Aviso da " + barbershopName + "*\n\n";
                // Sincrono: so conta como alcancado quem a Evolution aceitou. Com o envio
                // assincrono o relatorio contava a base inteira, mesmo com o WhatsApp fora.
                messenger.sendSync(customer.getPhoneNumber(), header + message, "DISPARO");
                contatosSucesso.add("✅ " + customer.getName());
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[DISPARO] Interrompido durante o envio para {}", customer.getPhoneNumber());
                break;
            } catch (Exception e) {
                falhas++;
                log.warn("[DISPARO] Falha ao enviar para {}: {}", customer.getPhoneNumber(), e.getMessage(), e);
            }
        }
        String falhasTxt = falhas > 0 ? "\n❌ Falharam: " + falhas : "";
        sendMsg(adminPhone, "📊 *Relatório de Disparo*\nTotal alcançado: " + contatosSucesso.size() + falhasTxt + "\n\n" + String.join("\n", contatosSucesso), "ADMIN");
    }
}
