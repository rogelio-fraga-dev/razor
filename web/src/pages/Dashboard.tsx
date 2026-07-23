import { useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { Cockpit } from "../components/Cockpit";
import { ActivationChecklist } from "../components/ActivationChecklist";
import { useAuth } from "../auth/AuthContext";
import { EmptyState } from "../components/EmptyState";
import { SkeletonStats, Skeleton } from "../components/Skeleton";
import { Tour, isTourDone, type TourStep } from "../components/Tour";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { AppointmentView, FinancialSummary, PaymentStatus } from "../api/types";
import { money, timeShort } from "../lib/format";

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

export function Dashboard() {
  const { session } = useAuth();
  const fin = useApi<FinancialSummary>(() => api.get("/api/admin/reports/financial"), []);
  const agenda = useApi<AppointmentView[]>(() => api.get("/api/admin/reports/agenda"), []);
  const s = fin.data;

  // Tour de primeiro acesso: dispara uma vez, depois que a tela montou.
  const [showTour, setShowTour] = useState(false);
  useEffect(() => {
    if (!isTourDone()) {
      const t = setTimeout(() => setShowTour(true), 900);
      return () => clearTimeout(t);
    }
  }, []);

  return (
    <Layout title="Painel">
      <Cockpit name={session?.name ?? ""} />

      <ActivationChecklist />

      <div className="stat-grid" data-tour="kpis">
        {fin.loading ? (
          <SkeletonStats count={4} />
        ) : (
          <>
            <div className="stat stat-hero">
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
              <div className="label">Recebido</div>
              <div className="value">{s ? money(s.payments.pix.value + s.payments.presencial.value) : "—"}</div>
              <div className="sub">{s ? `${s.payments.pix.count + s.payments.presencial.count} pagamentos · PIX + presencial` : ""}</div>
            </div>
            <div className="stat">
              <div className="label">Pendente</div>
              <div className="value">{s ? money(s.payments.pendente.value) : "—"}</div>
              <div className="sub">{s ? `${s.payments.pendente.count} a receber` : ""}</div>
            </div>
          </>
        )}
      </div>

      <div className="card" data-tour="agenda">
        <div className="card-head">
          <h3>Agenda de hoje</h3>
          {agenda.data && agenda.data.length > 0 && (
            <span className="badge badge-muted">{agenda.data.length} agendamentos</span>
          )}
        </div>
        {agenda.loading ? (
          <div className="stack" style={{ padding: 20, gap: 14 }}>
            {[0, 1, 2].map((i) => (
              <div key={i} className="row" style={{ gap: 14 }}>
                <Skeleton w={48} h={13} />
                <Skeleton w="60%" h={40} style={{ borderRadius: 8 }} />
              </div>
            ))}
          </div>
        ) : agenda.error ? (
          <div className="empty error">{agenda.error}</div>
        ) : !agenda.data || agenda.data.length === 0 ? (
          <EmptyState
            icon="📅"
            title="Nenhum corte marcado para hoje — ainda."
            hint="Quando um cliente pedir horário no WhatsApp, a IA agenda e o horário aparece aqui sozinho."
          />
        ) : (
          <div className="timeline">
            {agenda.data.map((a, i) => {
              const b = a.paymentStatus ? PAYMENT_BADGE[a.paymentStatus] : null;
              return (
                <div className="tl-item" key={i}>
                  <span className="tl-time">{timeShort(a.time)}</span>
                  <span className="tl-rail"><span className="tl-dot" /></span>
                  <div className="tl-card">
                    <span className="tl-client">{a.customerName || "—"}</span>
                    {a.subscriber && <span className="badge badge-plan">💎</span>}
                    <span className="tl-service">{a.service || "—"}</span>
                    {b && <span className={`badge ${b.cls}`}>{b.label}</span>}
                    <span className="tl-barber">{a.professionalName ? `✂ ${a.professionalName}` : "sem barbeiro"}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {showTour && <Tour steps={TOUR} onFinish={() => setShowTour(false)} />}
    </Layout>
  );
}
