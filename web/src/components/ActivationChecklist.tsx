// Checklist de ativação (UX_BLUEPRINT §3.1): substitui o dashboard "mudo" do primeiro
// acesso por um caminho claro. Estados calculados dos dados reais; some quando completo.
// Nada aqui é presumido: se os dados não carregam, o card não aparece (em vez de fingir
// que a barbearia está zerada e mandar o dono refazer coisa já feita).
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../lib/useApi";
import { getOnce } from "../lib/dedupe";
import { api } from "../api/client";
import type { AppointmentView, BotSettings, BotStatus, Customer, Professional } from "../api/types";
import { Skeleton } from "./Skeleton";

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

/** Recolhido/expandido fica no navegador: quem já sabe o que falta não quer a lista aberta sempre. */
const COLLAPSED_KEY = "razor.checklist_collapsed";

function buildItems(data: ChecklistData): Item[] {
  const textsCustomized = !!(
    data.botTexts.addressText || data.botTexts.servicesText || data.botTexts.instagramUrl
  );

  return [
    {
      id: "whatsapp",
      done: data.status.whatsappConnected,
      label: "Conectar o WhatsApp da barbearia",
      hint: "Escaneie o QR em Configurações — 2 minutos.",
      to: "/settings",
    },
    {
      id: "team",
      done: data.professionals.some((p) => p.active),
      label: "Cadastrar sua equipe",
      hint: "A IA agenda por barbeiro.",
      to: "/professionals",
    },
    {
      id: "bot_texts",
      done: textsCustomized,
      label: "Personalizar os textos do bot",
      hint: "Endereço, tabela de preços e Instagram que a IA envia.",
      to: "/settings",
    },
    {
      id: "test",
      done: data.customers.length > 0,
      label: "Testar o bot",
      hint: "Mande um “oi” do seu número pessoal para o WhatsApp conectado.",
      to: "/settings",
    },
    {
      id: "first_app",
      done: data.agenda.length > 0,
      label: "Primeiro agendamento pela IA",
      hint: "Peça um horário na conversa — e veja aparecer aqui.",
      to: "/",
    },
  ];
}

export function ActivationChecklist() {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSED_KEY) === "true");

  const { data, loading, error } = useApi<ChecklistData>(async () => {
    const [status, botTexts, professionals, customers, agenda] = await Promise.all([
      getOnce<BotStatus>("/api/admin/settings/bot/status"),
      api.get<BotSettings>("/api/admin/settings/bot"),
      api.get<Professional[]>("/api/admin/professionals"),
      api.get<Customer[]>("/api/admin/customers"),
      getOnce<AppointmentView[]>("/api/admin/reports/agenda"),
    ]);
    return { status, botTexts, professionals, customers, agenda };
  }, []);

  const items = data ? buildItems(data) : null;
  const doneCount = items ? items.filter((i) => i.done).length : 0;
  const complete = !!items && doneCount === items.length;

  // Ativado: o card se despede de vez e apaga o estado guardado — se algo regredir depois
  // (WhatsApp cair, por exemplo) ele volta aberto, não recolhido e passando despercebido.
  useEffect(() => {
    if (complete) localStorage.removeItem(COLLAPSED_KEY);
  }, [complete]);

  const toggle = () => {
    setCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem(COLLAPSED_KEY, String(next));
      return next;
    });
  };

  if (loading) {
    return (
      <div className="card checklist">
        <div className="card-pad row" style={{ gap: 16 }}>
          <Skeleton w={160} h={14} />
          <Skeleton w="40%" h={14} />
        </div>
      </div>
    );
  }

  // Sem dados reais não dá para dizer o que falta configurar — melhor não mostrar nada
  // do que mostrar um checklist inventado.
  if (error || !items) return null;
  if (complete) return null;

  const pct = Math.round((doneCount / items.length) * 100);

  return (
    <div className="card checklist" data-tour="checklist">
      <div className="card-head cl-head" onClick={toggle} role="presentation">
        <h3>Comece por aqui</h3>
        <div className="row" style={{ gap: 12 }}>
          <span className="badge badge-plan mono">
            {doneCount} de {items.length} · {pct}%
          </span>
          <button
            className="btn btn-ghost btn-sm"
            onClick={(e) => {
              e.stopPropagation();
              toggle();
            }}
            aria-expanded={!collapsed}
            aria-controls="checklist-items"
            title={collapsed ? "Expandir" : "Recolher"}
          >
            {collapsed ? "▾" : "▴"}
          </button>
        </div>
      </div>

      {/* A barra de progresso fica visível mesmo recolhido — é a informação que resume o resto. */}
      <div className="cl-progress" aria-hidden="true">
        <span style={{ width: `${pct}%` }} />
      </div>

      {!collapsed && (
        <div className="cl-items" id="checklist-items">
          {items.map((i) => (
            <Link key={i.id} to={i.to} className={`cl-item ${i.done ? "done" : ""}`}>
              <span className="cl-check" aria-hidden="true">{i.done ? "✓" : ""}</span>
              <span className="grow">
                <span className="cl-label">{i.label}</span>
                {!i.done && <span className="cl-hint">{i.hint}</span>}
              </span>
              {!i.done && <span className="cl-go" aria-hidden="true">→</span>}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
