// Cabeçalho do Painel: a "cabine" da barbearia. Em vez de cair direto numa grade de
// cards (cara de template), o dono vê o estado do negócio dele agora — saudação, data
// em mono, e o pulso do sistema (IA ativa / desligada).
import { useApi } from "../lib/useApi";
import { getOnce } from "../lib/dedupe";
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
  const { data, loading, error } = useApi<BotStatus>(() => getOnce("/api/admin/settings/bot/status"), []);
  // Sem status (carregando ou falha) nao da para afirmar que a IA parou: dizer "PARADA" faria o
  // dono religar algo que esta funcionando. Estado neutro ate saber de verdade.
  const unknown = !data || !!error;
  const live = !unknown && data.whatsappConnected && data.botEnabled;
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
          {unknown
            ? loading
              ? "Verificando o atendimento automático…"
              : "Status indisponível no momento — não foi possível consultar a IA."
            : live
              ? "A IA está no comando do seu WhatsApp — pode cortar tranquilo."
              : "O atendimento automático está parado. Ative para a IA voltar a agendar."}
        </p>
      </div>
      <div
        className={`ck-pulse ${unknown ? "unknown" : live ? "on" : "off"}`}
        title={unknown ? "Status indisponível" : live ? "IA ativa" : "IA parada"}
      >
        <span className="ck-ring" aria-hidden="true" />
        <span className="ck-label mono">{unknown ? (loading ? "…" : "STATUS INDISPONÍVEL") : live ? "IA ATIVA" : "PARADA"}</span>
      </div>
    </header>
  );
}
