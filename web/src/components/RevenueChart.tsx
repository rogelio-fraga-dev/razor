// Gráfico de evolução do faturamento. Recebe pontos já apurados (dados reais do relatório
// financeiro) e só desenha — sem série de exemplo: período sem movimento aparece como
// período sem movimento.
import type { SeriesPoint } from "../lib/reportSeries";
import { money } from "../lib/format";

const SVG_W = 700;
const SVG_H = 170;
const PAD_X = 40;
const PAD_TOP = 30;
const PAD_BOTTOM = 25;

export function RevenueChart({ points }: { points: SeriesPoint[] }) {
  if (points.length < 2) return null;

  const drawW = SVG_W - PAD_X * 2;
  const drawH = SVG_H - PAD_TOP - PAD_BOTTOM;
  const maxVal = Math.max(...points.map((p) => p.value));
  const hasMovement = maxVal > 0;
  // Sem faturamento no período a linha assenta na base em vez de dividir por zero.
  const scale = hasMovement ? maxVal : 1;

  const plotted = points.map((p, i) => ({
    ...p,
    x: PAD_X + (i / (points.length - 1)) * drawW,
    y: PAD_TOP + drawH - (p.value / scale) * drawH,
  }));

  const polyline = plotted.map((p) => `${p.x},${p.y}`).join(" ");
  const baseline = SVG_H - PAD_BOTTOM;
  const polygon = `${plotted[0].x},${baseline} ${polyline} ${plotted[plotted.length - 1].x},${baseline}`;

  return (
    <div style={{ width: "100%", position: "relative" }}>
      {/* `role="img"` faz o leitor de tela tratar o SVG como uma imagem única e ignorar os
          <text> internos. Sem aria-label o gráfico era anunciado como "imagem", sem nome — e o
          dado, que é a razão de ele existir, ficava inacessível. A tabela abaixo é a alternativa
          completa; o rótulo aqui é o resumo. */}
      <svg
        width="100%"
        height="210"
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={
          hasMovement
            ? `Gráfico de evolução do faturamento, de ${money(Math.min(...points.map((p) => p.value)))} a ${money(maxVal)} no período.`
            : "Gráfico de evolução do faturamento. Nenhum faturamento registrado no período."
        }
      >
        <defs>
          <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" style={{ stopColor: "var(--chart-fill-stop, #3E63FF)" }} stopOpacity="0.45" />
            <stop offset="100%" style={{ stopColor: "var(--chart-fill-stop, #3E63FF)" }} stopOpacity="0.0" />
          </linearGradient>
        </defs>

        <line x1={PAD_X} y1={PAD_TOP} x2={SVG_W - PAD_X} y2={PAD_TOP} style={{ stroke: "var(--chart-grid, rgba(255,255,255,0.06))" }} strokeDasharray="4" />
        <line x1={PAD_X} y1={PAD_TOP + drawH / 2} x2={SVG_W - PAD_X} y2={PAD_TOP + drawH / 2} style={{ stroke: "var(--chart-grid, rgba(255,255,255,0.06))" }} strokeDasharray="4" />
        <line x1={PAD_X} y1={baseline} x2={SVG_W - PAD_X} y2={baseline} style={{ stroke: "var(--chart-grid, rgba(255,255,255,0.1))" }} />

        {hasMovement && <polygon className="chart-area" points={polygon} fill="url(#chartGrad)" />}
        <polyline className="chart-line" pathLength={1} fill="none" style={{ stroke: "var(--chart-line, #37E2FF)" }} strokeWidth="3" points={polyline} />

        {plotted.map((p, i) => (
          <g key={p.label} className="chart-pt" style={{ ["--d" as string]: `${0.15 + (i / Math.max(1, plotted.length - 1)) * 0.9}s` }}>
            <circle cx={p.x} cy={p.y} r="5" style={{ fill: "var(--chart-dot, #3E63FF)", stroke: "var(--chart-line, #37E2FF)" }} strokeWidth="2.5" />
            {p.value > 0 && (
              <text x={p.x} y={p.y - 10} textAnchor="middle" fill="var(--chrome)" fontSize="10" fontWeight="700" fontFamily="var(--font-mono)">
                {money(p.value)}
              </text>
            )}
            <text x={p.x} y={SVG_H - 6} textAnchor="middle" fill="var(--steel)" fontSize="10" fontWeight="600" fontFamily="var(--font-mono)">
              {p.label}
            </text>
          </g>
        ))}
      </svg>

      {/* Alternativa textual completa: o resumo no aria-label diz a faixa, mas quem usa leitor
          de tela também precisa dos valores ponto a ponto — que é o que o gráfico mostra. */}
      <table className="sr-only">
        <caption>Faturamento por período</caption>
        <thead>
          <tr>
            <th scope="col">Período</th>
            <th scope="col">Valor</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.label}>
              <td>{p.label}</td>
              <td>{money(p.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {!hasMovement && (
        <p className="faint" style={{ textAlign: "center", fontSize: 13, margin: "4px 0 0" }}>
          Nenhum faturamento registrado no período.
        </p>
      )}
    </div>
  );
}
