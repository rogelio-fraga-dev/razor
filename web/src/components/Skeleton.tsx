// Skeleton screens (UX_BLUEPRINT §5): carregamento com a forma do conteúdo real —
// nunca "Carregando…". Shimmer sutil, respeita prefers-reduced-motion via CSS.
import type { CSSProperties } from "react";

export function Skeleton({ w = "100%", h = 14, style }: { w?: number | string; h?: number | string; style?: CSSProperties }) {
  return <span className="skeleton" style={{ width: w, height: h, ...style }} aria-hidden="true" />;
}

/** Linhas de tabela/lista em carregamento. */
export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="stack" style={{ gap: 14, padding: "18px 20px" }} aria-busy="true" aria-label="Carregando">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="row" style={{ gap: 16 }}>
          <Skeleton w={i % 2 ? "34%" : "26%"} h={13} />
          <Skeleton w="18%" h={13} />
          <Skeleton w="12%" h={13} />
          <span className="grow" />
          <Skeleton w={64} h={22} style={{ borderRadius: 999 }} />
        </div>
      ))}
    </div>
  );
}

/** Grade de KPIs em carregamento (forma dos cards .stat). */
export function SkeletonStats({ count = 4 }: { count?: number }) {
  return (
    <div className="stat-grid" aria-busy="true" aria-label="Carregando indicadores">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="stat">
          <Skeleton w="52%" h={12} />
          <Skeleton w="70%" h={24} style={{ marginTop: 12, display: "block" }} />
          <Skeleton w="40%" h={11} style={{ marginTop: 8, display: "block" }} />
        </div>
      ))}
    </div>
  );
}
