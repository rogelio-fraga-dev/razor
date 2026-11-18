// Painel do dono. Regra da tela: todo número aqui vem do backend. Onde não há dado real
// (meta de faturamento, taxa de ocupação, ranking sem movimento) a tela diz que não há —
// nunca preenche com exemplo, porque o dono usa esses números para decidir dinheiro.
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { Hint } from "../components/Hint";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Cockpit } from "../components/Cockpit";
import { ActivationChecklist } from "../components/ActivationChecklist";
import { RevenueChart } from "../components/RevenueChart";
import { MonthlyGoalCard } from "../components/MonthlyGoalCard";
import { AiImpactCard } from "../components/AiImpactCard";
import { EmptyState } from "../components/EmptyState";
import { SkeletonStats, Skeleton } from "../components/Skeleton";
import { Tour, isTourDone, type TourStep } from "../components/Tour";
import { useAuth } from "../auth/AuthContext";
import { useApi } from "../lib/useApi";
import { getOnce } from "../lib/dedupe";
import { api } from "../api/client";
import { fetchRevenueSeries, firstDayOfMonth, type SeriesPeriod } from "../lib/reportSeries";
import { money, timeShort, todayIso } from "../lib/format";
import type {
  AppointmentView,
  CommissionReport,
  FinancialSummary,
  Goals,
  PaymentStatus,
} from "../api/types";

const PAYMENT_BADGE: Record<PaymentStatus, { cls: string; label: string }> = {
  PIX: { cls: "badge-green", label: "PIX" },
  PRESENCIAL: { cls: "badge-blue", label: "Presencial" },
  PENDENTE: { cls: "badge-amber", label: "Pendente" },
};

const TOUR: TourStep[] = [
  {
    target: '[data-tour="kpis"]',
    title: "Seu caixa, em tempo real",
    text: "Faturamento, atendimentos e o que ainda está pendente — atualizado a cada agendamento que a IA fecha.",
  },
  {
    target: '[data-tour="agenda"]',
    title: "A agenda de hoje",
    text: "Tudo o que a IA marcou aparece aqui, com barbeiro, pagamento e o selo 💎 dos assinantes do clube.",
  },
  {
    target: '[data-tour="nav-settings"]',
    title: "O controle do bot",
    text: "Em Configurações você conecta o WhatsApp, liga/desliga a IA e edita os textos que ela envia.",
  },
  {
    target: '[data-tour="nav-customers"]',
    title: "Sua base de clientes",
    text: "Quem conversa com o bot entra sozinho aqui — com fidelidade e plano de assinatura.",
  },
  {
    target: '[data-tour="profile"]',
    title: "Seu perfil",
    text: "Nome e senha da sua conta. Pronto — a barbearia é sua. 👊",
  },
];

const PERIODS: SeriesPeriod[] = ["7D", "30D", "MÊS"];

export function Dashboard() {
  const { session } = useAuth();
  const [period, setPeriod] = useState<SeriesPeriod>("7D");

  const month = useApi<FinancialSummary>(
    () => api.get(`/api/admin/reports/financial?from=${firstDayOfMonth()}&to=${todayIso()}`),
    [],
  );
  const agenda = useApi<AppointmentView[]>(() => getOnce("/api/admin/reports/agenda"), []);
  const commissions = useApi<CommissionReport>(() => api.get("/api/admin/reports/commissions"), []);
  const goals = useApi<Goals>(() => api.get("/api/admin/settings/goals"), []);
  const series = useApi(() => fetchRevenueSeries(period), [period]);

  const m = month.data;

  const [showTour, setShowTour] = useState(false);
  useEffect(() => {
    if (!isTourDone()) {
      const t = setTimeout(() => setShowTour(true), 900);
      return () => clearTimeout(t);
    }
  }, []);

  return (
    <Layout title="Painel Analítico">
      <Cockpit name={session?.name ?? ""} />

      <ActivationChecklist />

      {/* O diferencial do produto em números: o que o bot fez sozinho neste mês (V42) */}
      <AiImpactCard />

      {/* Meta do mês: alvo definido pelo dono × faturamento real */}
      {!month.loading && !goals.loading && !month.error && !goals.error && (
        <MonthlyGoalCard
          goal={goals.data?.monthlyRevenueGoal ?? null}
          revenue={m?.totalRevenue ?? 0}
        />
      )}

      {/* KPIs do mês corrente — todos vindos de /reports/financial */}
      {month.loading ? (
        <SkeletonStats count={4} />
      ) : month.error ? (
        <div className="card error">{month.error}</div>
      ) : (
        <div className="bento-grid" data-tour="kpis">
          <div className="card kpi-card card-edge">
            <div className="kpi-header">
              <span className="kpi-title">Faturamento do mês</span>
            </div>
            <div className="kpi-value mono">{money(m?.totalRevenue)}</div>
            <div className="kpi-sub">
              {m
                ? `Serviços ${money(m.servicesRevenue)} · produtos ${money(m.productsRevenue)}`
                : "—"}
            </div>
          </div>

          <div className="card kpi-card">
            <div className="kpi-header">
              <span className="kpi-title">Atendimentos no mês</span>
            </div>
            <div className="kpi-value mono">{m?.totalAppointments ?? 0}</div>
            <div className="kpi-sub">
              {m
                ? `${m.categories.combo} combo · ${m.categories.barba} barba · ${m.categories.simples} corte`
                : "—"}
            </div>
          </div>

          <div className="card kpi-card">
            <div className="kpi-header">
              <span className="kpi-title">Tíquete médio<Hint term="ticketMedio" label="tíquete médio" /></span>
            </div>
            <div className="kpi-value mono">{money(m?.averageTicket)}</div>
            <div className="kpi-sub">
              {m && m.productsSold > 0
                ? `Serviços por atendimento · ${m.productsSold} produto(s) à parte`
                : "Serviços por atendimento no mês"}
            </div>
          </div>

          <div className="card kpi-card">
            <div className="kpi-header">
              <span className="kpi-title">A receber</span>
            </div>
            <div className="kpi-value mono">{money(m?.payments.pendente.value)}</div>
            <div className="kpi-sub">
              {m ? `${m.payments.pendente.count} pagamento(s) em aberto` : "—"}
            </div>
          </div>
        </div>
      )}

      {/* Evolução do faturamento + agenda do dia */}
      <div className="bento-grid">
        <div className="card bento-col-8">
          {/* Mesmo cabeçalho dos outros cards (antes era um bloco solto, colado na borda). */}
          <div className="card-head">
            <h3>Evolução de faturamento</h3>
            <div style={{ display: "flex", gap: 6 }}>
              {PERIODS.map((p) => (
                <button
                  key={p}
                  className={`btn btn-sm ${period === p ? "btn-primary" : "btn-ghost"}`}
                  onClick={() => setPeriod(p)}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          <div className="card-pad">
            <p className="faint" style={{ fontSize: 12.5, margin: "0 0 12px" }}>
              Valores apurados no período ({period})
            </p>
            {series.loading ? (
              <Skeleton h={210} />
            ) : series.error ? (
              <div className="error" role="alert">{series.error}</div>
            ) : (
              <RevenueChart points={series.data ?? []} />
            )}
          </div>
        </div>

        <div className="card bento-col-4" data-tour="agenda">
          <div className="card-head">
            <h3>Agenda de hoje</h3>
            {agenda.data && agenda.data.length > 0 && (
              <span className="badge badge-muted">{agenda.data.length}</span>
            )}
          </div>
          {agenda.loading ? (
            <div className="stack" style={{ padding: 20, gap: 14 }}>
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} h={40} style={{ borderRadius: 8 }} />
              ))}
            </div>
          ) : agenda.error ? (
            <ErrorState message={agenda.error} onRetry={agenda.reload} />
          ) : !agenda.data || agenda.data.length === 0 ? (
            <EmptyState
              icon="📅"
              title="Nenhum corte marcado para hoje — ainda."
              hint="Quando um cliente pedir horário no WhatsApp, a IA agenda e o horário aparece aqui sozinho."
              action={<Link className="btn btn-ghost" to="/agenda">Marcar um horário</Link>}
            />
          ) : (
            <div className="timeline">
              {agenda.data.map((a, i) => {
                const badge = a.paymentStatus ? PAYMENT_BADGE[a.paymentStatus] : null;
                return (
                  <div className="tl-item" key={i}>
                    <span className="tl-time">{timeShort(a.time)}</span>
                    <span className="tl-rail"><span className="tl-dot" /></span>
                    <div className="tl-card">
                      <span className="tl-client">{a.customerName || "—"}</span>
                      {a.subscriber && <span className="badge badge-plan">💎</span>}
                      <span className="tl-service">{a.service || "—"}</span>
                      {badge && <span className={`badge ${badge.cls}`}>{badge.label}</span>}
                      <span className="tl-barber">
                        {a.professionalName ? `✂ ${a.professionalName}` : "sem barbeiro"}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Comissões do dia — /reports/commissions */}
      <div className="card">
        <div className="card-head">
          <h3>Comissões de hoje</h3>
          {commissions.data && commissions.data.items.length > 0 && (
            <span className="badge badge-plasma mono">
              {commissions.data.items.length} barbeiro(s)
            </span>
          )}
        </div>

        {commissions.loading ? (
          <div className="card-pad stack" style={{ gap: 10 }}>
            {[0, 1, 2].map((i) => <Skeleton key={i} h={38} />)}
          </div>
        ) : commissions.error ? (
          <ErrorState message={commissions.error} onRetry={commissions.reload} />
        ) : !commissions.data || commissions.data.items.length === 0 ? (
          <EmptyState
            icon="✂"
            title="Nenhuma comissão apurada hoje."
            hint="Assim que um atendimento for concluído, a comissão do barbeiro aparece aqui."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Profissional</th>
                  <th style={{ textAlign: "right" }}>Atendimentos</th>
                  <th style={{ textAlign: "right" }}>Serviços</th>
                  <th style={{ textAlign: "right" }}>Produtos</th>
                  <th style={{ textAlign: "right" }}>Comissão</th>
                </tr>
              </thead>
              <tbody>
                {commissions.data.items.map((it) => (
                  <tr key={it.professionalId}>
                    <td>
                      <strong>{it.professionalName}</strong>
                      <div style={{ fontSize: 11, color: "var(--steel)" }}>
                        {it.commissionPercent}% de comissão
                      </div>
                    </td>
                    <td className="num">{it.atendimentos}</td>
                    <td className="num">{money(it.faturamento)}</td>
                    <td className="num">
                      {it.produtos > 0 ? money(it.faturamentoProdutos) : <span className="faint">—</span>}
                    </td>
                    <td className="num" style={{ color: "var(--signal)", fontWeight: 700 }}>
                      {money(it.comissao)}
                    </td>
                  </tr>
                ))}
                <tr>
                  <td><strong>Total</strong></td>
                  <td className="num" />
                  <td className="num" />
                  <td className="num" />
                  <td className="num" style={{ fontWeight: 700 }}>{money(commissions.data.total)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showTour && <Tour steps={TOUR} onFinish={() => setShowTour(false)} />}
    </Layout>
  );
}
