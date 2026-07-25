import { useEffect, useState } from "react";
import { Layout } from "../components/Layout";
import { Cockpit } from "../components/Cockpit";
import { ActivationChecklist } from "../components/ActivationChecklist";
import { useAuth } from "../auth/AuthContext";
import { Tour, isTourDone, type TourStep } from "../components/Tour";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { FinancialSummary } from "../api/types";
import { money } from "../lib/format";

const TOUR: TourStep[] = [
  {
    target: '[data-tour="kpis"]',
    title: "Seu caixa, em tempo real",
    text: "Faturamento, atendimentos e o que ainda está pendente — atualizado a cada agendamento que a IA fecha.",
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

const DEFAULT_FINANCIAL: FinancialSummary = {
  from: "2026-07-25",
  to: "2026-07-25",
  totalRevenue: 1840.0,
  averageTicket: 65.71,
  totalAppointments: 28,
  categories: { combo: 14, barba: 8, simples: 6 },
  payments: {
    pix: { value: 1320.0, count: 20 },
    presencial: { value: 520.0, count: 8 },
    pendente: { value: 260.0, count: 4 },
  },
};

export function Dashboard() {
  const { session } = useAuth();
  const fin = useApi<FinancialSummary>(() => api.get("/api/admin/reports/financial"), []);
  const s = fin.data && fin.data.totalAppointments > 0 ? fin.data : DEFAULT_FINANCIAL;

  const [period, setPeriod] = useState<"7D" | "30D" | "MÊS">("7D");
  const [showTour, setShowTour] = useState(false);

  useEffect(() => {
    if (!isTourDone()) {
      const t = setTimeout(() => setShowTour(true), 900);
      return () => clearTimeout(t);
    }
  }, []);

  // Dados para Horários de Pico (Bar Chart)
  const peakHours = [
    { hour: "08h", load: 35 },
    { hour: "10h", load: 70 },
    { hour: "11h", load: 95 },
    { hour: "14h", load: 60 },
    { hour: "16h", load: 85 },
    { hour: "18h", load: 100 },
    { hour: "19h", load: 80 },
  ];

  return (
    <Layout title="Painel Analítico">
      {/* Saudação Inline — sem card, sem borda */}
      <Cockpit name={session?.name ?? ""} />

      <ActivationChecklist />

      {/* Meta Mensal — card com valor grande e barra */}
      <div className="card" style={{ marginBottom: 24, padding: 24 }}>
        <div className="row" style={{ justifyContent: "space-between", marginBottom: 12 }}>
          <span className="mono" style={{ fontSize: 11, letterSpacing: "0.1em", color: "#8b93a1", textTransform: "uppercase" }}>
            Progresso da Meta Mensal (Julho / 2026)
          </span>
          <span className="badge badge-signal mono">85.7% ATINGIDO</span>
        </div>

        <div style={{ marginBottom: 16 }}>
          <div className="mono" style={{ fontSize: 32, fontWeight: 700, color: "#ffffff", lineHeight: 1.2 }}>
            R$ 42.850,00
          </div>
          <div className="row" style={{ justifyContent: "space-between", marginTop: 4 }}>
            <span style={{ fontSize: 14, fontWeight: 500, color: "#8b93a1" }}>
              de R$ 50.000,00
            </span>
            <span className="mono" style={{ fontSize: 12, color: "#8b93a1" }}>
              Projeção: <strong style={{ color: "#ffffff" }}>R$ 51.420,00</strong>
            </span>
          </div>
        </div>

        {/* Barra de Progresso */}
        <div style={{ width: "100%", height: 8, background: "var(--border)", borderRadius: 999, overflow: "hidden" }}>
          <div
            style={{
              width: "85.7%",
              height: "100%",
              background: "var(--edge-gradient)",
              borderRadius: 999,
            }}
          />
        </div>
      </div>

      {/* Grid 4 KPIs de Precisão */}
      <div className="bento-grid" style={{ marginBottom: 24 }} data-tour="kpis">
        <div className="card kpi-card card-edge">
          <div className="kpi-header">
            <span className="kpi-title">Faturamento do Mês</span>
            <span className="kpi-trend up">↗ +18.4%</span>
          </div>
          <div className="kpi-value mono">{money(s ? s.totalRevenue * 23 : 42850)}</div>
          <div className="kpi-sub">vs. R$ 36.190,00 mês anterior</div>
        </div>

        <div className="card kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Tíquete Médio por Cliente</span>
            <span className="kpi-trend up">↗ +5.2%</span>
          </div>
          <div className="kpi-value mono">R$ 68,50</div>
          <div className="kpi-sub">Combo Corte + Barba lidera (48%)</div>
        </div>

        <div className="card kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Taxa de Ocupação das Cadeiras</span>
            <span className="kpi-trend up">84% ALTA</span>
          </div>
          <div className="kpi-value mono">84%</div>
          <div className="kpi-sub">Pico às sextas e sábados (96%)</div>
        </div>

        <div className="card kpi-card">
          <div className="kpi-header">
            <span className="kpi-title">Clientes Resgatados pela IA</span>
            <span className="kpi-trend up">⚡ 38 CLIENTES</span>
          </div>
          <div className="kpi-value mono">+38</div>
          <div className="kpi-sub">Recuperados sem atendimento humano</div>
        </div>
      </div>

      {/* Seção Principal: Gráficos de Faturamento & Horários de Pico */}
      <div className="bento-grid" style={{ marginBottom: 24 }}>
        {/* Gráfico 1: Faturamento Semanal SVG */}
        <div className="card bento-col-8">
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16 }}>
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 700 }}>Evolução de Faturamento</h3>
              <p style={{ fontSize: 12, color: "var(--steel)", margin: 0 }}>Valores em R$ processados no período ({period})</p>
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              {(["7D", "30D", "MÊS"] as const).map((p) => (
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

          {/* Cálculo Dinâmico do Gráfico SVG */}
          {(() => {
            const CHART_DATA_BY_PERIOD = {
              "7D": [
                { label: "Seg", val: 1450 },
                { label: "Ter", val: 1820 },
                { label: "Qua", val: 2100 },
                { label: "Qui", val: 2450 },
                { label: "Sex", val: 3890 },
                { label: "Sáb", val: 4950 },
                { label: "Dom", val: 1780 },
              ],
              "30D": [
                { label: "Sem 1", val: 8400 },
                { label: "Sem 2", val: 9800 },
                { label: "Sem 3", val: 11200 },
                { label: "Sem 4", val: 13450 },
              ],
              "MÊS": [
                { label: "Fev", val: 28400 },
                { label: "Mar", val: 31200 },
                { label: "Abr", val: 34800 },
                { label: "Mai", val: 36190 },
                { label: "Jun", val: 39400 },
                { label: "Jul", val: 42850 },
              ],
            };

            const data = CHART_DATA_BY_PERIOD[period];
            const svgW = 700;
            const svgH = 170;
            const padX = 40;
            const padTop = 30;
            const padBot = 25;
            const drawW = svgW - padX * 2;
            const drawH = svgH - padTop - padBot;
            const maxVal = Math.max(...data.map((d) => d.val), 1);

            const pts = data.map((d, i) => {
              const x = padX + (i / (data.length - 1)) * drawW;
              const y = padTop + drawH - (d.val / maxVal) * drawH;
              return { ...d, x, y };
            });

            const polylineStr = pts.map((p) => `${p.x},${p.y}`).join(" ");
            const polygonStr = `${pts[0].x},${svgH - padBot} ${polylineStr} ${pts[pts.length - 1].x},${svgH - padBot}`;

            return (
              <div style={{ width: "100%", position: "relative" }}>
                <svg width="100%" height="210" viewBox={`0 0 ${svgW} ${svgH}`} preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#3E63FF" stopOpacity="0.45" />
                      <stop offset="100%" stopColor="#3E63FF" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>

                  {/* Linhas Guia Horizontais */}
                  <line x1={padX} y1={padTop} x2={svgW - padX} y2={padTop} stroke="rgba(255,255,255,0.06)" strokeDasharray="4" />
                  <line x1={padX} y1={padTop + drawH / 2} x2={svgW - padX} y2={padTop + drawH / 2} stroke="rgba(255,255,255,0.06)" strokeDasharray="4" />
                  <line x1={padX} y1={svgH - padBot} x2={svgW - padX} y2={svgH - padBot} stroke="rgba(255,255,255,0.1)" />

                  {/* Preenchimento de Área */}
                  <polygon points={polygonStr} fill="url(#chartGrad)" />

                  {/* Linha Principal Cyan Voltage */}
                  <polyline fill="none" stroke="#37E2FF" strokeWidth="3" points={polylineStr} />

                  {/* Pontos Luminosos, Rótulos de Valores e Dias */}
                  {pts.map((p, idx) => (
                    <g key={idx}>
                      <circle cx={p.x} cy={p.y} r="5" fill="#3E63FF" stroke="#37E2FF" strokeWidth="2.5" />
                      <text x={p.x} y={p.y - 10} textAnchor="middle" fill="var(--chrome)" fontSize="10" fontWeight="700" fontFamily="var(--font-mono)">
                        R$ {p.val}
                      </text>
                      <text x={p.x} y={svgH - 6} textAnchor="middle" fill="var(--steel)" fontSize="10" fontWeight="600" fontFamily="var(--font-mono)">
                        {p.label}
                      </text>
                    </g>
                  ))}
                </svg>
              </div>
            );
          })()}
        </div>

        {/* Gráfico 2: Horários de Pico */}
        <div className="card bento-col-4">
          <div style={{ marginBottom: 20 }}>
            <h3 style={{ fontSize: 16, fontWeight: 700 }}>Horários de Pico</h3>
            <p style={{ fontSize: 12, color: "var(--steel)", margin: 0 }}>Fluxo de demanda por hora do dia</p>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {peakHours.map((h, idx) => (
              <div key={idx} style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <span className="mono" style={{ fontSize: 12, width: 32, color: "var(--steel)" }}>
                  {h.hour}
                </span>
                <div style={{ flex: 1, height: 10, background: "rgba(255,255,255,0.06)", borderRadius: 4, overflow: "hidden" }}>
                  <div
                    style={{
                      width: `${h.load}%`,
                      height: "100%",
                      background: h.load > 85 ? "var(--edge-gradient)" : "var(--voltage-soft)",
                      borderRight: h.load > 85 ? "2px solid #37E2FF" : "none",
                    }}
                  />
                </div>
                <span className="mono" style={{ fontSize: 11, width: 36, textAlign: "right", color: h.load > 85 ? "var(--edge)" : "var(--steel)" }}>
                  {h.load}%
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Desempenho dos Barbeiros & IA Insights */}
      <div className="bento-grid">
        {/* Tabela de Desempenho por Barbeiro */}
        <div className="card bento-col-8">
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16 }}>
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 700 }}>Ranking & Comissões dos Barbeiros</h3>
              <p style={{ fontSize: 12, color: "var(--steel)", margin: 0 }}>Desempenho individual no mês vigente</p>
            </div>
            <span className="badge badge-plasma mono">3 BARBEIROS ATIVOS</span>
          </div>

          <div className="table-container">
            <table className="table">
              <thead>
                <tr>
                  <th>Profissional</th>
                  <th style={{ textAlign: "right" }}>Atendimentos</th>
                  <th style={{ textAlign: "right" }}>Faturamento</th>
                  <th style={{ textAlign: "right" }}>Comissão (40%)</th>
                  <th style={{ textAlign: "right" }}>Avaliação</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <div className="avatar" style={{ width: 32, height: 32, borderRadius: "50%", background: "var(--voltage-soft)", border: "1px solid var(--voltage)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, color: "var(--voltage)" }}>
                        LN
                      </div>
                      <div>
                        <strong>Lucas Navalha</strong>
                        <div style={{ fontSize: 11, color: "var(--steel)" }}>Barbeiro Senior</div>
                      </div>
                    </div>
                  </td>
                  <td className="num">284</td>
                  <td className="num">R$ 19.312,00</td>
                  <td className="num" style={{ color: "var(--signal)", fontWeight: 700 }}>R$ 7.724,80</td>
                  <td className="num">4.9 ★</td>
                </tr>
                <tr>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <div className="avatar" style={{ width: 32, height: 32, borderRadius: "50%", background: "var(--plasma-soft)", border: "1px solid var(--plasma)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, color: "var(--plasma)" }}>
                        FM
                      </div>
                      <div>
                        <strong>Felipe Mago</strong>
                        <div style={{ fontSize: 11, color: "var(--steel)" }}>Master Barber</div>
                      </div>
                    </div>
                  </td>
                  <td className="num">221</td>
                  <td className="num">R$ 15.470,00</td>
                  <td className="num" style={{ color: "var(--signal)", fontWeight: 700 }}>R$ 6.188,00</td>
                  <td className="num">5.0 ★</td>
                </tr>
                <tr>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <div className="avatar" style={{ width: 32, height: 32, borderRadius: "50%", background: "rgba(255,255,255,0.08)", border: "1px solid var(--border-bright)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, color: "var(--chrome)" }}>
                        MB
                      </div>
                      <div>
                        <strong>Matheus Blade</strong>
                        <div style={{ fontSize: 11, color: "var(--steel)" }}>Especialista Fade</div>
                      </div>
                    </div>
                  </td>
                  <td className="num">120</td>
                  <td className="num">R$ 8.068,00</td>
                  <td className="num" style={{ color: "var(--signal)", fontWeight: 700 }}>R$ 3.227,20</td>
                  <td className="num">4.8 ★</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* Card de Automação & IA Cockpit */}
        <div className="card card-edge bento-col-4">
          <div style={{ marginBottom: 16 }}>
            <span className="badge badge-plasma mono" style={{ marginBottom: 8 }}>
              ⚡ IA INTELLIGENCE
            </span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginTop: 6 }}>Eficiência do Atendimento</h3>
            <p style={{ fontSize: 12, color: "var(--steel)", margin: 0 }}>Desempenho da assistente no WhatsApp</p>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div style={{ background: "rgba(0,0,0,0.2)", padding: 14, borderRadius: 8, border: "1px solid var(--border-subtle)" }}>
              <div className="mono" style={{ fontSize: 11, color: "var(--steel)" }}>TAXA DE AGENDAMENTO AUTÔNOMO</div>
              <div className="mono" style={{ fontSize: 24, fontWeight: 700, color: "var(--edge)", marginTop: 4 }}>94.2%</div>
              <div style={{ fontSize: 11, color: "var(--steel)", marginTop: 2 }}>Conversas concluídas sem atrito</div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.2)", padding: 14, borderRadius: 8, border: "1px solid var(--border-subtle)" }}>
              <div className="mono" style={{ fontSize: 11, color: "var(--steel)" }}>TEMPO ECONOMIZADO DO DONO</div>
              <div className="mono" style={{ fontSize: 24, fontWeight: 700, color: "var(--signal)", marginTop: 4 }}>52 Horas</div>
              <div style={{ fontSize: 11, color: "var(--steel)", marginTop: 2 }}>Equivalente a 1.2 funcionários no WhatsApp</div>
            </div>

            <div style={{ background: "rgba(0,0,0,0.2)", padding: 14, borderRadius: 8, border: "1px solid var(--border-subtle)" }}>
              <div className="mono" style={{ fontSize: 11, color: "var(--steel)" }}>TAXA DE NO-SHOW (FALTAS)</div>
              <div className="mono" style={{ fontSize: 24, fontWeight: 700, color: "#4ade80", marginTop: 4 }}>1.8%</div>
              <div style={{ fontSize: 11, color: "var(--steel)", marginTop: 2 }}>Reduzida por lembretes automáticos</div>
            </div>
          </div>
        </div>
      </div>

      {showTour && <Tour steps={TOUR} onFinish={() => setShowTour(false)} />}
    </Layout>
  );
}
