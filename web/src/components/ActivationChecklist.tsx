// Checklist de ativação (UX_BLUEPRINT §3.1): substitui o dashboard "mudo" do primeiro
// acesso por um caminho claro. Estados calculados dos dados reais; some quando completo.
import { Link } from "react-router-dom";
import { useApi } from "../lib/useApi";
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
  done: boolean;
  label: string;
  hint: string;
  to: string;
}

export function ActivationChecklist() {
  const { data, loading } = useApi<ChecklistData>(async () => {
    const [status, botTexts, professionals, customers, agenda] = await Promise.all([
      api.get<BotStatus>("/api/admin/settings/bot/status"),
      api.get<BotSettings>("/api/admin/settings/bot"),
      api.get<Professional[]>("/api/admin/professionals"),
      api.get<Customer[]>("/api/admin/customers"),
      api.get<AppointmentView[]>("/api/admin/reports/agenda"),
    ]);
    return { status, botTexts, professionals, customers, agenda };
  }, []);

  if (loading) {
    return (
      <div className="card checklist">
        <div className="card-pad row" style={{ gap: 16 }}>
          <Skeleton w={140} h={14} />
          <Skeleton w="40%" h={14} />
        </div>
      </div>
    );
  }
  if (!data) return null;

  const textsCustomized = !!(
    data.botTexts.addressText || data.botTexts.servicesText || data.botTexts.instagramUrl
  );

  const items: Item[] = [
    {
      done: data.status.whatsappConnected,
      label: "Conectar o WhatsApp",
      hint: "Escaneie o QR em Configurações — 2 minutos.",
      to: "/settings",
    },
    {
      done: data.professionals.some((p) => p.active),
      label: "Cadastrar sua equipe",
      hint: "A IA agenda por barbeiro.",
      to: "/professionals",
    },
    {
      done: textsCustomized,
      label: "Personalizar os textos do bot",
      hint: "Endereço, tabela de preços e Instagram que a IA envia.",
      to: "/settings",
    },
    {
      done: data.customers.length > 0,
      label: "Testar o bot",
      hint: "Mande um “oi” do seu número pessoal para o WhatsApp conectado.",
      to: "/settings",
    },
    {
      done: data.agenda.length > 0,
      label: "Primeiro agendamento pela IA",
      hint: "Peça um horário na conversa — e veja aparecer aqui.",
      to: "/",
    },
  ];

  const doneCount = items.filter((i) => i.done).length;
  if (doneCount === items.length) return null; // ativado — o card se despede

  return (
    <div className="card checklist" data-tour="checklist">
      <div className="card-head">
        <h3>Comece por aqui</h3>
        <span className="badge badge-plan mono">{doneCount} / {items.length}</span>
      </div>
      <div className="cl-progress" aria-hidden="true">
        <span style={{ width: `${(doneCount / items.length) * 100}%` }} />
      </div>
      <div className="cl-items">
        {items.map((i) => (
          <Link key={i.label} to={i.to} className={`cl-item ${i.done ? "done" : ""}`}>
            <span className="cl-check" aria-hidden="true">{i.done ? "✓" : ""}</span>
            <span className="grow">
              <span className="cl-label">{i.label}</span>
              {!i.done && <span className="cl-hint">{i.hint}</span>}
            </span>
            {!i.done && <span className="cl-go" aria-hidden="true">→</span>}
          </Link>
        ))}
      </div>
    </div>
  );
}
