// Ocupação e retenção: as duas métricas que respondem "e agora, o que eu faço?".
//
// Ficam na mesma tela porque respondem à mesma pergunta por lados opostos. Ocupação baixa com
// faturamento alto significa cadeira sobrando; retenção caindo com ocupação alta significa que a
// casa está cheia de gente que não volta. Separá-las faria o dono comparar dois relatórios.
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { CustomerAtRisk, Occupancy, Retention as RetentionData } from "../api/types";
import { dateBr, percent } from "../lib/format";

function phoneBr(p: string): string {
  const d = p.replace(/\D/g, "").replace(/^55/, "");
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return p;
}

function hours(n: number): string {
  return `${n.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h`;
}

/** Lista de clientes para contatar. O telefone é o ponto: sem ele o número não vira ação. */
function CustomerList({ items, empty }: { items: CustomerAtRisk[]; empty: string }) {
  if (items.length === 0) {
    return <p className="muted" style={{ margin: 0, fontSize: 14 }}>{empty}</p>;
  }
  return (
    <div style={{ overflowX: "auto" }}>
      <table className="table">
        <thead>
          <tr>
            <th>Cliente</th>
            <th>Telefone</th>
            <th>Última visita</th>
            <th style={{ textAlign: "right" }}>Parado há</th>
            <th style={{ textAlign: "right" }}>Costuma voltar em</th>
          </tr>
        </thead>
        <tbody>
          {items.map((c) => (
            <tr key={c.customerId}>
              <td style={{ fontWeight: 550 }}>
                {c.name || "Sem nome"}
                {c.subscriber && <span className="badge badge-plan" style={{ marginLeft: 8 }}>assinante</span>}
              </td>
              <td className="mono">{phoneBr(c.phone)}</td>
              <td className="muted">{c.lastVisit ? dateBr(c.lastVisit) : "nunca veio"}</td>
              <td className="num">{c.lastVisit ? `${c.daysSinceLastVisit} dias` : "—"}</td>
              {/* Os dois juntos é que justificam a cobrança: "40 dias" sozinho não diz se é muito. */}
              <td className="num muted">{c.expectedIntervalDays} dias</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function RetentionPage() {
  const occupancy = useApi<Occupancy>(() => api.get("/api/admin/reports/occupancy"), []);
  const retention = useApi<RetentionData>(() => api.get("/api/admin/reports/retention"), []);

  const o = occupancy.data;
  const r = retention.data;

  return (
    <Layout title="Ocupação e retenção">
      {/* ---------- Ocupação ---------- */}
      <div className="card">
        <div className="card-head">
          <h3>Ocupação das cadeiras</h3>
          {o && <span className="badge badge-muted mono">{o.openDays} dias abertos no período</span>}
        </div>

        {occupancy.loading ? (
          <SkeletonRows rows={3} />
        ) : occupancy.error ? (
          <div className="empty error" role="alert">{occupancy.error}</div>
        ) : o ? (
          <>
            <div className="cfg-hero">
              <div className="cfg-hero-main">
                <h3 className="cfg-hero-title">
                  {o.rate === null ? "Sem horário disponível no período" : `${percent(o.rate)} do tempo vendido`}
                </h3>
                <p className="cfg-hero-sub">
                  {hours(o.bookedHours)} atendidos de {hours(o.availableHours)} disponíveis
                  {o.unassignedAppointments > 0 &&
                    ` · ${o.unassignedAppointments} atendimento(s) sem barbeiro definido`}
                </p>
              </div>
              {o.rate !== null && (
                <div className="cfg-hero-kpi">
                  <span className="cfg-hero-pct mono">{percent(o.rate)}</span>
                </div>
              )}
            </div>

            {o.professionals.length === 0 ? (
              <EmptyState icon="✂" title="Nenhum barbeiro ativo." hint="Cadastre a equipe em Profissionais para medir a ocupação." />
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Barbeiro</th>
                      <th style={{ textAlign: "right" }}>Disponível</th>
                      <th style={{ textAlign: "right" }}>Vendido</th>
                      <th style={{ textAlign: "right" }}>Atendimentos</th>
                      <th style={{ textAlign: "right" }}>Ocupação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {o.professionals.map((p) => (
                      <tr key={p.professionalId}>
                        <td style={{ fontWeight: 550 }}>{p.professionalName}</td>
                        <td className="num muted">{hours(p.availableHours)}</td>
                        <td className="num">{hours(p.bookedHours)}</td>
                        <td className="num muted">{p.appointments}</td>
                        <td className="num">{p.rate === null ? "—" : percent(p.rate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="faint" style={{ fontSize: 12.5, margin: "12px 0 0" }}>
              Disponível já desconta dias fechados e folgas. Vendido usa a <b>duração real</b> de
              cada serviço — contar atendimentos faria uma agenda de combos parecer mais vazia que
              uma de cortes.
            </p>
          </>
        ) : null}
      </div>

      {/* ---------- Retenção ---------- */}
      <div className="card">
        <div className="card-head">
          <h3>Retenção da base</h3>
          {r && r.returnRate !== null && (
            <span className="badge badge-muted mono">{percent(r.returnRate)} já voltaram</span>
          )}
        </div>

        {retention.loading ? (
          <SkeletonRows rows={3} />
        ) : retention.error ? (
          <div className="empty error" role="alert">{retention.error}</div>
        ) : !r || r.totalCustomers === 0 ? (
          <EmptyState
            icon="👥"
            title="Base ainda vazia."
            hint="Quem falar com o bot entra aqui sozinho. Com histórico, esta tela mostra quem está prestes a sumir."
          />
        ) : (
          <>
            <div className="bento-grid" style={{ marginBottom: 18 }}>
              <div className="card kpi-card card-edge">
                <div className="kpi-header"><span className="kpi-title">Ativos</span></div>
                <div className="kpi-value mono">{r.counts.ativo}</div>
                <div className="kpi-sub">dentro do ritmo deles</div>
              </div>
              <div className="card kpi-card">
                <div className="kpi-header"><span className="kpi-title">Em risco</span></div>
                <div className="kpi-value mono" style={{ color: "var(--amber)" }}>{r.counts.emRisco}</div>
                <div className="kpi-sub">passaram do intervalo habitual</div>
              </div>
              <div className="card kpi-card">
                <div className="kpi-header"><span className="kpi-title">Sumidos</span></div>
                <div className="kpi-value mono" style={{ color: "var(--red)" }}>{r.counts.sumido}</div>
                <div className="kpi-sub">passaram do dobro</div>
              </div>
              <div className="card kpi-card">
                <div className="kpi-header"><span className="kpi-title">Novos</span></div>
                <div className="kpi-value mono">{r.counts.novo}</div>
                <div className="kpi-sub">cedo demais para julgar</div>
              </div>
            </div>

            <p className="faint" style={{ fontSize: 12.5, margin: "0 0 18px" }}>
              O prazo é o de <b>cada cliente</b>, não um número fixo: quem corta a cada 15 dias e
              sumiu há 40 é um problema; quem corta a cada 60 e sumiu há 40 está em dia.{" "}
              {r.medianIntervalDays !== null
                ? `Na sua barbearia a mediana é de ${r.medianIntervalDays} dias.`
                : `Ainda não há histórico suficiente para calcular o ritmo da casa — vale ${r.defaultIntervalDays} dias.`}
            </p>

            <h4 style={{ margin: "0 0 10px", fontSize: 15 }}>
              Prestes a sumir {r.counts.emRisco > r.atRisk.length && (
                <span className="faint" style={{ fontWeight: 400, fontSize: 12.5 }}>
                  (os {r.atRisk.length} mais atrasados de {r.counts.emRisco})
                </span>
              )}
            </h4>
            <CustomerList items={r.atRisk} empty="Ninguém atrasado no momento." />

            <h4 style={{ margin: "22px 0 10px", fontSize: 15 }}>
              Já sumiram {r.counts.sumido > r.lost.length && (
                <span className="faint" style={{ fontWeight: 400, fontSize: 12.5 }}>
                  (os {r.lost.length} mais atrasados de {r.counts.sumido})
                </span>
              )}
            </h4>
            <CustomerList items={r.lost} empty="Ninguém sumido — sua base está voltando." />
          </>
        )}
      </div>
    </Layout>
  );
}
