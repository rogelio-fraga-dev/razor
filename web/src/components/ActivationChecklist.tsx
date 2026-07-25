// Checklist de ativação: Guia passo a passo para primeiro acesso.
// Visível com 5 tarefas claras até o usuário concluir a configuração; some quando 100% pronto.
import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { AppointmentView, BotSettings, BotStatus, Customer, Professional } from "../api/types";

interface ChecklistData {
  status: BotStatus;
  botTexts: BotSettings;
  professionals: Professional[];
  customers: Customer[];
  agenda: AppointmentView[];
}

interface Item {
  id: string;
  done: boolean;
  label: string;
  hint: string;
  to: string;
}

const DEFAULT_STATUS: BotStatus = {
  instanceName: "barber",
  whatsappState: "close",
  whatsappConnected: false,
  aiConfigured: false,
  botEnabled: false,
};

const DEFAULT_BOT_TEXTS: BotSettings = {
  displayName: null,
  addressText: null,
  addressMapsUrl: null,
  servicesText: null,
  scheduleUrl: null,
  instagramUrl: null,
  pixKey: null,
  pixCopyPaste: null,
  paymentInfo: null,
  openingTime: null,
  closingTime: null,
  workingDays: null,
  welcomeText: null,
  reminderText: null,
  reviewText: null,
  fidelityRewardText: null,
  cancellationPolicy: null,
  botPersona: null,
};

export function ActivationChecklist() {
  const [dismissed, setDismissed] = useState(false);

  const { data } = useApi<ChecklistData>(async () => {
    try {
      const [status, botTexts, professionals, customers, agenda] = await Promise.all([
        api.get<BotStatus>("/api/admin/settings/bot/status").catch(() => DEFAULT_STATUS),
        api.get<BotSettings>("/api/admin/settings/bot").catch(() => DEFAULT_BOT_TEXTS),
        api.get<Professional[]>("/api/admin/professionals").catch(() => []),
        api.get<Customer[]>("/api/admin/customers").catch(() => []),
        api.get<AppointmentView[]>("/api/admin/reports/agenda").catch(() => []),
      ]);
      return { status, botTexts, professionals, customers, agenda };
    } catch {
      return {
        status: DEFAULT_STATUS,
        botTexts: DEFAULT_BOT_TEXTS,
        professionals: [],
        customers: [],
        agenda: [],
      };
    }
  }, []);

  if (dismissed) return null;

  // Se não houver data ainda (carregando sem cache), assume dados iniciais padrão de primeiro acesso
  const currentData: ChecklistData = data ?? {
    status: DEFAULT_STATUS,
    botTexts: DEFAULT_BOT_TEXTS,
    professionals: [],
    customers: [],
    agenda: [],
  };

  const statusObj = currentData.status ?? DEFAULT_STATUS;
  const botTextsObj = currentData.botTexts ?? DEFAULT_BOT_TEXTS;
  const profsList = Array.isArray(currentData.professionals) ? currentData.professionals : [];
  const custsList = Array.isArray(currentData.customers) ? currentData.customers : [];
  const agendaList = Array.isArray(currentData.agenda) ? currentData.agenda : [];

  // Suporta tanto o DTO do backend Java (connected) quanto o modelo frontend (whatsappConnected)
  const isConnected = !!(
    (statusObj as unknown as { connected?: boolean })?.connected ?? statusObj?.whatsappConnected
  );

  const textsCustomized = !!(
    botTextsObj?.addressText || botTextsObj?.servicesText || botTextsObj?.instagramUrl
  );

  const items: Item[] = [
    {
      id: "whatsapp",
      done: isConnected,
      label: "Conectar o WhatsApp da Barbearia",
      hint: "Escaneie o QR Code em Configurações para ativar a IA (2 min).",
      to: "/settings",
    },
    {
      id: "team",
      done: profsList.some((p) => p && p.active),
      label: "Cadastrar sua Equipe de Barbeiros",
      hint: "Adicione os profissionais para a IA agendar na cadeira certa.",
      to: "/professionals",
    },
    {
      id: "bot_texts",
      done: textsCustomized,
      label: "Personalizar os Textos do Bot",
      hint: "Defina o endereço da loja, preços e Instagram que o bot envia.",
      to: "/settings",
    },
    {
      id: "test",
      done: isConnected && custsList.length > 0,
      label: "Testar o Bot no WhatsApp",
      hint: "Envie um “oi” do seu celular para testar a resposta da IA.",
      to: "/settings",
    },
    {
      id: "first_app",
      done: isConnected && agendaList.length > 0,
      label: "Primeiro Agendamento Automático",
      hint: "Agende um horário de teste e veja aparecer no painel.",
      to: "/",
    },
  ];

  const doneCount = items.filter((i) => i.done).length;

  // Só oculta se TODAS as 5 tarefas estiverem realmente concluídas (incluindo WhatsApp conectado)
  if (doneCount === items.length) return null;

  const pct = Math.round((doneCount / items.length) * 100);

  return (
    <div
      className="card checklist"
      style={{
        marginBottom: 24,
        padding: 0,
        background: "var(--surface)",
        border: "1px solid var(--border-hi)",
        borderRadius: "var(--radius-lg, 12px)",
        overflow: "hidden",
      }}
    >
      {/* Topo do Checklist */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "16px 20px",
          borderBottom: "1px solid var(--border)",
          background: "rgba(62, 99, 255, 0.04)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 18 }}>🚀</span>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--text)" }}>
            Configuração Inicial do Sistema
          </h3>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span className="mono" style={{ fontSize: 12, fontWeight: 600, color: "var(--accent)" }}>
            {doneCount} de {items.length} concluídos ({pct}%)
          </span>
          <button
            onClick={() => setDismissed(true)}
            style={{
              background: "transparent",
              border: "none",
              color: "var(--text-faint)",
              fontSize: 14,
              cursor: "pointer",
              padding: "4px 8px",
            }}
            title="Ocultar checklist"
          >
            ✕
          </button>
        </div>
      </div>

      {/* Barra de Progresso */}
      <div style={{ width: "100%", height: 4, background: "var(--border)", position: "relative" }}>
        <div
          style={{
            width: `${pct}%`,
            height: "100%",
            background: "linear-gradient(68deg, #3e63ff, #37e2ff)",
            transition: "width 0.4s ease",
          }}
        />
      </div>

      {/* Lista dos 5 Itens */}
      <div style={{ display: "flex", flexDirection: "column" }}>
        {items.map((item) => (
          <Link
            key={item.id}
            to={item.to}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 14,
              padding: "14px 20px",
              borderBottom: "1px solid var(--border)",
              textDecoration: "none",
              background: item.done ? "transparent" : "rgba(255, 255, 255, 0.01)",
              opacity: item.done ? 0.6 : 1,
              transition: "background 0.15s ease",
            }}
          >
            {/* Ícone de Check / Pendente */}
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: "50%",
                background: item.done ? "#4ade80" : "transparent",
                border: item.done ? "none" : "2px solid var(--text-faint)",
                color: item.done ? "#0a0a0c" : "transparent",
                display: "grid",
                placeItems: "center",
                fontWeight: 800,
                fontSize: 13,
                flexShrink: 0,
              }}
            >
              {item.done ? "✓" : ""}
            </div>

            {/* Conteúdo do Item */}
            <div style={{ flex: 1 }}>
              <div
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  color: item.done ? "var(--text-muted)" : "var(--text)",
                  textDecoration: item.done ? "line-through" : "none",
                }}
              >
                {item.label}
              </div>
              {!item.done && (
                <div style={{ fontSize: 12.5, color: "var(--text-muted)", marginTop: 2 }}>
                  {item.hint}
                </div>
              )}
            </div>

            {/* Seta de Ação */}
            {!item.done && (
              <span style={{ fontSize: 14, fontWeight: 700, color: "var(--accent)" }}>
                Configurar →
              </span>
            )}
          </Link>
        ))}
      </div>
    </div>
  );
}

