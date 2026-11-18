import { Link } from "react-router";
import { ErrorState } from "./ErrorState";
import { SkeletonRows } from "./Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { ReviewSummary } from "../api/types";
import { dateBr, percent } from "../lib/format";

function stars(n: number): string {
  return "★".repeat(n) + "☆".repeat(Math.max(0, 5 - n));
}

/**
 * Avaliações dos clientes (V46): média e distribuição das notas dos últimos 90 dias e as mais
 * recentes. Nota baixa aparece aqui com o comentário — ela nunca vai para o Google.
 */
export function ReviewsCard() {
  const { data, loading, error, reload } = useApi<ReviewSummary>(() => api.get("/api/admin/reviews/summary?days=90"), []);

  return (
    <div className="card">
      <div className="card-head">
        <h3>Avaliações dos clientes</h3>
        {data && <span className="badge badge-muted mono">últimos {data.days} dias</span>}
      </div>
      <div className="card-pad stack" style={{ gap: 14 }}>
        {loading && !data ? (
          <SkeletonRows rows={3} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data ? null : data.count === 0 ? (
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            {data.enabled
              ? "Nenhuma nota ainda. Depois que uma comanda fecha, o bot pede uma nota de 1 a 5 ao cliente."
              : "O pedido de avaliação está desligado."}{" "}
            <Link to="/settings?tab=bot">Configurar</Link>
          </p>
        ) : (
          <>
            <div className="row wrap" style={{ gap: 24, alignItems: "center" }}>
              <div>
                <div className="mono" style={{ fontSize: 34, fontWeight: 700, lineHeight: 1 }}>
                  {data.average?.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
                </div>
                <div className="review-stars" aria-hidden="true">{stars(Math.round(data.average ?? 0))}</div>
                <div className="muted" style={{ fontSize: 13 }}>
                  {data.count} {data.count === 1 ? "nota" : "notas"}
                  {data.responseRate != null && ` · ${percent(Math.round(data.responseRate * 100))} responderam`}
                </div>
              </div>
              <div className="review-dist" style={{ flex: "1 1 200px" }}>
                {[5, 4, 3, 2, 1].map((n) => {
                  const q = data.distribution[String(n)] ?? 0;
                  const pct = data.count > 0 ? (q / data.count) * 100 : 0;
                  return (
                    <div className="review-dist-row" key={n}>
                      <span className="mono">{n}★</span>
                      <span className="review-dist-bar" aria-hidden="true">
                        <span style={{ width: `${pct}%` }} />
                      </span>
                      <span className="mono muted">{q}</span>
                    </div>
                  );
                })}
              </div>
            </div>
            {!data.googleLinkConfigured && (
              <p className="muted" style={{ margin: 0, fontSize: 13 }}>
                Sem link do Google configurado: quem dá 4–5 só recebe um obrigado.{" "}
                <Link to="/settings?tab=bot">Adicionar link</Link>
              </p>
            )}
            <ul className="review-list">
              {data.recent.map((r) => (
                <li key={r.id}>
                  <div className="row wrap" style={{ gap: 8, justifyContent: "space-between" }}>
                    <strong>{r.customerName}</strong>
                    <span className="review-stars" aria-label={`nota ${r.rating} de 5`}>{stars(r.rating)}</span>
                  </div>
                  <span className="muted" style={{ fontSize: 13 }}>
                    {dateBr(r.ratedAt.slice(0, 10))}
                    {r.professionalName && ` · com ${r.professionalName}`}
                  </span>
                  {r.comment && <span style={{ fontSize: 14 }}>“{r.comment}”</span>}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
