// Progresso da meta do mês. A meta é do dono (definida em Configurações) e o realizado é o
// faturamento apurado — sem meta definida o card não aparece, em vez de inventar um alvo.
import { Link } from "react-router";
import { money } from "../lib/format";

const MONTH_FMT = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" });

export function MonthlyGoalCard({ goal, revenue }: { goal: number | null; revenue: number }) {
  if (!goal || goal <= 0) {
    return (
      <div className="card card-pad">
        <div className="row between wrap" style={{ gap: 12 }}>
          <div>
            <h3 style={{ fontSize: 15, fontWeight: 700, margin: 0 }}>Meta do mês</h3>
            <p className="muted" style={{ fontSize: 13, margin: "4px 0 0" }}>
              Defina quanto você quer faturar neste mês e acompanhe o progresso aqui.
            </p>
          </div>
          <Link className="btn btn-sm btn-ghost" to="/settings">
            Definir meta
          </Link>
        </div>
      </div>
    );
  }

  const pct = (revenue / goal) * 100;
  const reached = pct >= 100;
  const barWidth = Math.min(pct, 100);
  const remaining = goal - revenue;

  return (
    <div className="card card-pad">
      <div className="row between" style={{ marginBottom: 12 }}>
        <span
          className="mono"
          style={{ fontSize: 11, letterSpacing: "0.1em", color: "var(--steel)", textTransform: "uppercase" }}
        >
          Progresso da meta ({MONTH_FMT.format(new Date())})
        </span>
        <span className={`badge mono ${reached ? "badge-green" : "badge-muted"}`}>
          {pct.toFixed(1).replace(".", ",")}% ATINGIDO
        </span>
      </div>

      <div style={{ marginBottom: 16 }}>
        <div className="mono" style={{ fontSize: 32, fontWeight: 700, lineHeight: 1.2 }}>
          {money(revenue)}
        </div>
        <div className="row between" style={{ marginTop: 4 }}>
          <span style={{ fontSize: 14, fontWeight: 500, color: "var(--steel)" }}>de {money(goal)}</span>
          <span className="mono" style={{ fontSize: 12, color: "var(--steel)" }}>
            {reached ? "Meta batida 🎉" : <>Faltam <strong>{money(remaining)}</strong></>}
          </span>
        </div>
      </div>

      <div style={{ width: "100%", height: 8, background: "var(--border)", borderRadius: 999, overflow: "hidden" }}>
        <div
          style={{
            width: `${barWidth}%`,
            height: "100%",
            background: "var(--edge-gradient)",
            borderRadius: 999,
            transition: "width 0.4s ease",
          }}
        />
      </div>
    </div>
  );
}
