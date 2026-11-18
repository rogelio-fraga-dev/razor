import { useEffect, useRef, useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { CommissionReport, Professional } from "../api/types";
import { Avatar } from "../components/Avatar";
import { dateBr, money, percent, todayIso } from "../lib/format";
import { addDays, weekStart } from "../lib/agenda";
import { Hint } from "../components/Hint";
import { Link } from "react-router-dom";

type PeriodId = "today" | "yesterday" | "week" | "fortnight" | "month" | "custom";

/** [de, até] de cada chip. Quinzena = 1–15 ou 16–hoje (o mesmo corte do Acerto). */
function rangeOf(id: Exclude<PeriodId, "custom">): [string, string] {
  const today = todayIso();
  const day = Number(today.slice(8, 10));
  switch (id) {
    case "today":
      return [today, today];
    case "yesterday": {
      const y = addDays(today, -1);
      return [y, y];
    }
    case "week":
      return [weekStart(today), today];
    case "fortnight":
      return [`${today.slice(0, 8)}${day <= 15 ? "01" : "16"}`, today];
    case "month":
      return [`${today.slice(0, 8)}01`, today];
  }
}

const CHIPS: { id: Exclude<PeriodId, "custom">; label: string; total: string }[] = [
  { id: "today", label: "Hoje", total: "Total de comissões hoje" },
  { id: "yesterday", label: "Ontem", total: "Total de comissões ontem" },
  { id: "week", label: "Esta semana", total: "Total de comissões na semana" },
  { id: "fortnight", label: "Quinzena", total: "Total de comissões na quinzena" },
  { id: "month", label: "Este mês", total: "Total de comissões no mês" },
];

export function Commissions() {
  // Período em 1 toque (UX_BLUEPRINT §4.5 / F7): "ver a comissão de ontem" = menu → chip.
  const [period, setPeriod] = useState<PeriodId>("today");
  const [customDate, setCustomDate] = useState(todayIso());
  // Foco no seletor só quando a pessoa pediu "Outra data…" (o botão some e o foco iria embora).
  const pickerRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (period === "custom") pickerRef.current?.focus();
  }, [period]);

  const [from, to] = period === "custom" ? [customDate, customDate] : rangeOf(period);
  const { data, loading, error, reload } = useApi<CommissionReport>(
    () =>
      api.get(
        from === to
          ? `/api/admin/reports/commissions?date=${from}`
          : `/api/admin/reports/commissions?from=${from}&to=${to}`,
      ),
    [from, to],
  );
  // Só para a foto do barbeiro (V37); se falhar, a lista segue com as iniciais.
  const pros = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);
  const photoOf = (id: string) => pros.data?.find((p) => p.id === id)?.photoUrl ?? null;

  const chip = CHIPS.find((c) => c.id === period);
  const totalLabel = chip ? chip.total : `Total de comissões em ${dateBr(customDate)}`;
  const rangeLabel = from === to ? dateBr(from) : `${dateBr(from)} a ${dateBr(to)}`;

  return (
    <Layout
      title="Comissões"
      actions={
        <Link to="/commissions/settlement" className="btn btn-ghost btn-sm">
          Ir para o acerto
        </Link>
      }
    >
      <div className="period-chips" role="group" aria-label="Período">
        {CHIPS.map((c) => (
          <button
            key={c.id}
            className={`chip-btn ${period === c.id ? "active" : ""}`}
            aria-pressed={period === c.id}
            onClick={() => setPeriod(c.id)}
          >
            {c.label}
          </button>
        ))}
        {period === "custom" ? (
          <input
            className="input"
            type="date"
            value={customDate}
            onChange={(e) => e.target.value && setCustomDate(e.target.value)}
            style={{ width: 160, padding: "6px 10px" }}
            ref={pickerRef}
            aria-label="Data"
          />
        ) : (
          <button className="chip-btn" onClick={() => setPeriod("custom")}>
            Outra data…
          </button>
        )}
      </div>

      <div className="stat-grid" style={{ gridTemplateColumns: "minmax(260px, 340px)" }}>
        <div className="stat">
          <div className="label">
            {totalLabel}
            <Hint term="comissao" label="comissão" align="start" />
          </div>
          <div className="value">{data ? money(data.total) : "—"}</div>
          <div className="sub">
            {rangeLabel}
            {data ? ` · ${data.items.length} barbeiro(s) com atendimento` : ""}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Por profissional</h3>
        </div>
        {loading ? (
          <SkeletonRows rows={3} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.items.length === 0 ? (
          <EmptyState
            icon="％"
            title={from === to ? "Nenhum atendimento com barbeiro nesse dia." : "Nenhum atendimento com barbeiro nesse período."}
            hint="Comissões aparecem quando um agendamento tem barbeiro atribuído — a IA atribui sozinha quando o cliente escolhe."
            action={<Link className="btn btn-ghost" to="/agenda">Abrir a agenda</Link>}
          />
        ) : (
          // No celular a tabela vira um cartão por barbeiro (shell.css, .table-cards).
          <div style={{ overflowX: "auto" }}>
            <table className="table table-cards">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th>Atendimentos</th>
                  <th>Serviços</th>
                  <th>
                    Produtos
                    <Hint term="comissaoProduto" label="comissão por produto" />
                  </th>
                  <th>Taxa</th>
                  <th className="right">Comissão</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((c) => (
                  <tr key={c.professionalId}>
                    <td className="tc-head" style={{ fontWeight: 550 }}>
                      <span className="avatar-name">
                        <Avatar name={c.professionalName} photoUrl={photoOf(c.professionalId)} size={30} />
                        {c.professionalName}
                      </span>
                    </td>
                    <td data-label="Atendimentos">{c.atendimentos}</td>
                    <td data-label="Serviços" className="num">{money(c.faturamento)}</td>
                    <td data-label="Produtos">
                      {c.produtos > 0 ? (
                        <>
                          {money(c.faturamentoProdutos)}
                          <span className="faint" style={{ fontSize: 12 }}> · {c.produtos} un.</span>
                        </>
                      ) : (
                        <span className="faint">—</span>
                      )}
                    </td>
                    <td data-label="Taxa" className="muted">
                      {percent(c.commissionPercent)}
                      {c.produtos > 0 && c.productCommissionPercent !== c.commissionPercent && (
                        <span className="faint" style={{ fontSize: 12 }}>
                          {" "}· produtos {percent(c.productCommissionPercent)}
                        </span>
                      )}
                    </td>
                    <td data-label="Comissão" className="num" style={{ fontWeight: 600 }}>{money(c.comissao)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
