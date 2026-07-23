import { Layout } from "../components/Layout";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { AppointmentView, FinancialSummary, PaymentStatus } from "../api/types";
import { money, timeShort } from "../lib/format";

const PAYMENT_BADGE: Record<PaymentStatus, { cls: string; label: string }> = {
  PIX: { cls: "badge-green", label: "PIX" },
  PRESENCIAL: { cls: "badge-blue", label: "Presencial" },
  PENDENTE: { cls: "badge-amber", label: "Pendente" },
};

export function Dashboard() {
  const fin = useApi<FinancialSummary>(() => api.get("/api/admin/reports/financial"), []);
  const agenda = useApi<AppointmentView[]>(() => api.get("/api/admin/reports/agenda"), []);
  const s = fin.data;

  return (
    <Layout title="Painel">
      <div className="stat-grid">
        <div className="stat">
          <div className="label">Faturamento de hoje</div>
          <div className="value">{s ? money(s.totalRevenue) : "—"}</div>
          <div className="sub">Ticket médio {s ? money(s.averageTicket) : "—"}</div>
        </div>
        <div className="stat">
          <div className="label">Atendimentos</div>
          <div className="value">{s ? s.totalAppointments : "—"}</div>
          <div className="sub">
            {s ? `${s.categories.combo} combo · ${s.categories.barba} barba · ${s.categories.simples} corte` : ""}
          </div>
        </div>
        <div className="stat">
          <div className="label">Recebido (PIX + presencial)</div>
          <div className="value">{s ? money(s.payments.pix.value + s.payments.presencial.value) : "—"}</div>
          <div className="sub">{s ? `${s.payments.pix.count + s.payments.presencial.count} pagamentos` : ""}</div>
        </div>
        <div className="stat">
          <div className="label">Pendente</div>
          <div className="value">{s ? money(s.payments.pendente.value) : "—"}</div>
          <div className="sub">{s ? `${s.payments.pendente.count} a receber` : ""}</div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Agenda de hoje</h3>
          {agenda.data && <span className="badge badge-muted">{agenda.data.length} agendamentos</span>}
        </div>
        {agenda.loading ? (
          <div className="empty">Carregando…</div>
        ) : agenda.error ? (
          <div className="empty error">{agenda.error}</div>
        ) : !agenda.data || agenda.data.length === 0 ? (
          <div className="empty">Nenhum agendamento para hoje.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Horário</th>
                  <th>Cliente</th>
                  <th>Serviço</th>
                  <th>Barbeiro</th>
                  <th>Pagamento</th>
                </tr>
              </thead>
              <tbody>
                {agenda.data.map((a, i) => {
                  const b = a.paymentStatus ? PAYMENT_BADGE[a.paymentStatus] : null;
                  return (
                    <tr key={i}>
                      <td style={{ fontWeight: 600 }}>{timeShort(a.time)}</td>
                      <td>
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          {a.customerName || "—"}
                          {a.subscriber && <span className="badge badge-plan">💎 Assinante</span>}
                        </span>
                      </td>
                      <td>{a.service || "—"}</td>
                      <td className="muted">{a.professionalName || "—"}</td>
                      <td>{b ? <span className={`badge ${b.cls}`}>{b.label}</span> : "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
