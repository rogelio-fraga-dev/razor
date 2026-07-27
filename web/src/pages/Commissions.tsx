import { useState } from "react";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { CommissionReport } from "../api/types";
import { money, percent, todayIso } from "../lib/format";

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function Commissions() {
  const [date, setDate] = useState(todayIso());
  const [showPicker, setShowPicker] = useState(false);
  const { data, loading, error } = useApi<CommissionReport>(
    () => api.get(`/api/admin/reports/commissions?date=${date}`),
    [date],
  );

  const chips = [
    { label: "Hoje", value: todayIso() },
    { label: "Ontem", value: isoDaysAgo(1) },
    { label: "Anteontem", value: isoDaysAgo(2) },
  ];
  const isCustom = !chips.some((c) => c.value === date);

  return (
    <Layout
      title="Comissões"
      actions={
        <div className="chip-row">
          {chips.map((c) => (
            <button
              key={c.label}
              className={`chip-btn ${date === c.value ? "active" : ""}`}
              onClick={() => {
                setDate(c.value);
                setShowPicker(false);
              }}
            >
              {c.label}
            </button>
          ))}
          {showPicker || isCustom ? (
            <input
              className="input"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              style={{ width: 160, padding: "6px 10px" }}
              autoFocus
            />
          ) : (
            <button className="chip-btn" onClick={() => setShowPicker(true)}>
              Outra data…
            </button>
          )}
        </div>
      }
    >
      <div className="stat-grid">
        <div className="stat">
          <div className="label">Total de comissões no dia</div>
          <div className="value">{data ? money(data.total) : "—"}</div>
          <div className="sub">{data ? `${data.items.length} barbeiro(s) com atendimento` : ""}</div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Por profissional</h3>
        </div>
        {loading ? (
          <SkeletonRows rows={3} />
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : !data || data.items.length === 0 ? (
          <EmptyState
            icon="％"
            title="Nenhum atendimento com barbeiro nesse dia."
            hint="Comissões aparecem quando um agendamento tem barbeiro atribuído — a IA atribui sozinha quando o cliente escolhe."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th>Atendimentos</th>
                  <th>Serviços</th>
                  <th>Produtos</th>
                  <th>Taxa</th>
                  <th className="right">Comissão</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((c) => (
                  <tr key={c.professionalId}>
                    <td style={{ fontWeight: 550 }}>{c.professionalName}</td>
                    <td>{c.atendimentos}</td>
                    <td>{money(c.faturamento)}</td>
                    <td>
                      {c.produtos > 0 ? (
                        <>
                          {money(c.faturamentoProdutos)}
                          <span className="faint" style={{ fontSize: 12 }}> · {c.produtos} un.</span>
                        </>
                      ) : (
                        <span className="faint">—</span>
                      )}
                    </td>
                    <td className="muted">{percent(c.commissionPercent)}</td>
                    <td className="right" style={{ fontWeight: 600 }}>{money(c.comissao)}</td>
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
