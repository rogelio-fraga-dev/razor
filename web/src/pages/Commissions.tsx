import { useState } from "react";
import { Layout } from "../components/Layout";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { CommissionReport } from "../api/types";
import { money, percent, todayIso } from "../lib/format";

export function Commissions() {
  const [date, setDate] = useState(todayIso());
  const { data, loading, error } = useApi<CommissionReport>(
    () => api.get(`/api/admin/reports/commissions?date=${date}`),
    [date],
  );

  return (
    <Layout
      title="Comissões"
      actions={
        <input
          className="input"
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          style={{ width: 168 }}
        />
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
          <div className="empty">Carregando…</div>
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : !data || data.items.length === 0 ? (
          <div className="empty">Nenhum atendimento com barbeiro atribuído nesse dia.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th>Atendimentos</th>
                  <th>Faturamento</th>
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
