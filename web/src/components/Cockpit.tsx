// Cabeçalho do Painel: a "cabine" da barbearia. Em vez de cair direto numa grade de
// cards (cara de template), o dono vê o estado do negócio dele agora — saudação, data
// em mono, e o pulso do sistema (IA ativa / desligada).
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { BotStatus } from "../api/types";

function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Bom dia";
  if (h < 18) return "Boa tarde";
  return "Boa noite";
}

const DATE_FMT = new Intl.DateTimeFormat("pt-BR", {
  weekday: "long",
  day: "2-digit",
  month: "long",
});

export function Cockpit({ name }: { name: string }) {
  const { data } = useApi<BotStatus>(() => api.get("/api/admin/settings/bot/status"), []);
  const live = !!data && data.whatsappConnected && data.botEnabled;
  const firstName = (name || "").split(" ")[0] || "chefe";
  const today = DATE_FMT.format(new Date());

  return (
    <header className="cockpit">
      <div className="ck-main">
        <span className="ck-date mono">{today.toUpperCase()}</span>
        <h2 className="ck-title">
          {greeting()}, <em>{firstName}</em>.
        </h2>
        <p className="ck-sub">
          {live
            ? "A IA está no comando do seu WhatsApp — pode cortar tranquilo."
            : "O atendimento automático está parado. Ative para a IA voltar a agendar."}
        </p>
      </div>
      <div className={`ck-pulse ${live ? "on" : "off"}`} title={live ? "IA ativa" : "IA parada"}>
        <span className="ck-ring" aria-hidden="true" />
        <span className="ck-label mono">{live ? "IA ATIVA" : "PARADA"}</span>
      </div>
    </header>
  );
}
