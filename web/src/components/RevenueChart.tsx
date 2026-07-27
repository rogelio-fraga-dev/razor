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
      <svg width="100%" height="210" viewBox={`0 0 ${SVG_W} ${SVG_H}`} preserveAspectRatio="none" role="img">
        <defs>
          <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3E63FF" stopOpacity="0.45" />
            <stop offset="100%" stopColor="#3E63FF" stopOpacity="0.0" />
          </linearGradient>
        </defs>

        <line x1={PAD_X} y1={PAD_TOP} x2={SVG_W - PAD_X} y2={PAD_TOP} stroke="rgba(255,255,255,0.06)" strokeDasharray="4" />
        <line x1={PAD_X} y1={PAD_TOP + drawH / 2} x2={SVG_W - PAD_X} y2={PAD_TOP + drawH / 2} stroke="rgba(255,255,255,0.06)" strokeDasharray="4" />
        <line x1={PAD_X} y1={baseline} x2={SVG_W - PAD_X} y2={baseline} stroke="rgba(255,255,255,0.1)" />

        {hasMovement && <polygon points={polygon} fill="url(#chartGrad)" />}
        <polyline fill="none" stroke="#37E2FF" strokeWidth="3" points={polyline} />

        {plotted.map((p) => (
          <g key={p.label}>
            <circle cx={p.x} cy={p.y} r="5" fill="#3E63FF" stroke="#37E2FF" strokeWidth="2.5" />
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

      {!hasMovement && (
        <p className="faint" style={{ textAlign: "center", fontSize: 13, margin: "4px 0 0" }}>
          Nenhum faturamento registrado no período.
        </p>
      )}
    </div>
  );
}
