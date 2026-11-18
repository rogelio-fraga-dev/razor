// "O que a IA fez por você" (V42): o diferencial do Razor em números do mês. Tudo vem medido do
// backend (/reports/ai-impact) — período sem dado mostra zero/estado vazio, nunca estimativa.
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import { money } from "../lib/format";
import { Skeleton } from "./Skeleton";
import type { AiImpact } from "../api/types";

function plural(n: number, one: string, many: string) {
  return `${n.toLocaleString("pt-BR")} ${n === 1 ? one : many}`;
}

export function AiImpactCard() {
  const { data, loading, error, reload } = useApi<AiImpact>(
    () => api.get("/api/admin/reports/ai-impact?period=month"),
    [],
  );

  if (loading) {
    return (
      <div className="card ai-impact" aria-busy="true" aria-label="Carregando o que a IA fez">
        <Skeleton w="40%" h={12} />
        <Skeleton w="85%" h={30} style={{ display: "block", marginTop: 14 }} />
        <div className="ai-impact-stats" style={{ marginTop: 18 }}>
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} h={58} style={{ borderRadius: 10 }} />
          ))}
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="card ai-impact" role="alert">
        <div className="row between wrap" style={{ gap: 12 }}>
          <span className="error">{error ?? "Não foi possível carregar o que a IA fez."}</span>
          <button className="btn btn-sm btn-ghost" onClick={reload}>Tentar de novo</button>
        </div>
      </div>
    );
  }

  const d = data;
  const vazio =
    d.bot.appointments === 0 && d.botMessages === 0 && d.site.appointments === 0 &&
    d.confirmationsReceived === 0 && d.waitlistRefills === 0;

  return (
    <section className="card ai-impact" aria-labelledby="ai-impact-title">
      <div className="ai-impact-eyebrow mono" id="ai-impact-title">
        🤖 O que a IA fez por você · este mês
      </div>

      {vazio ? (
        <p className="ai-impact-headline">
          A IA ainda não marcou nenhum horário este mês.
          <span className="ai-impact-sub">
            Com o WhatsApp conectado e o bot ligado, cada corte que ela marcar aparece aqui — com o valor.
          </span>
        </p>
      ) : (
        <p className="ai-impact-headline">
          Este mês a IA marcou <strong>{plural(d.bot.appointments, "horário", "horários")}</strong>
          {" = "}
          <strong className="ai-impact-money">{money(d.bot.revenue)}</strong>
          <span className="ai-impact-sub">
            Sem ninguém parar a tesoura pra responder o WhatsApp
            {d.botMessagesOutsideHours > 0 && <> — e {plural(d.botMessagesOutsideHours, "resposta saiu", "respostas saíram")} com a barbearia fechada</>}.
          </span>
        </p>
      )}

      <div className="ai-impact-stats">
        <div className="ai-impact-stat">
          <span className="ai-impact-num mono">{d.botMessages.toLocaleString("pt-BR")}</span>
          <span className="ai-impact-label">mensagens respondidas</span>
          <span className="ai-impact-hint">{d.botMessagesOutsideHours.toLocaleString("pt-BR")} fora do horário</span>
        </div>
        <div className="ai-impact-stat">
          <span className="ai-impact-num mono">{d.rescuedCustomers.toLocaleString("pt-BR")}</span>
          <span className="ai-impact-label">clientes resgatados</span>
          <span className="ai-impact-hint">estavam sumidos e voltaram a marcar</span>
        </div>
        <div className="ai-impact-stat">
          <span className="ai-impact-num mono">{d.noShowsAvoided.toLocaleString("pt-BR")}</span>
          <span className="ai-impact-label">faltas evitadas</span>
          <span className="ai-impact-hint">
            {plural(d.confirmationsReceived, "confirmação", "confirmações")} · {plural(d.waitlistRefills, "vaga reaproveitada", "vagas reaproveitadas")}
          </span>
        </div>
        <div className="ai-impact-stat">
          <span className="ai-impact-num mono">{d.site.appointments.toLocaleString("pt-BR")}</span>
          <span className="ai-impact-label">pelo site</span>
          <span className="ai-impact-hint">{money(d.site.revenue)} em agendamentos online</span>
        </div>
      </div>
    </section>
  );
}
