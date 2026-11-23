// Saúde do sistema (V50): faixa de alerta no topo do painel (dono e recepção) e o cartão com os
// incidentes abertos e o histórico (Configurações → Status, só o dono). O monitor do backend abre
// e fecha os incidentes sozinho; aqui só se mostra e se aponta o caminho para resolver.
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { SkeletonRows } from "./Skeleton";
import { useApi } from "../lib/useApi";
import { api } from "../api/client";
import type { HealthOverview, IncidentView } from "../api/types";
import "./health.css";

const POLL_MS = 120_000;

function when(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function ActionLink({ i, canAct }: { i: IncidentView; canAct: boolean }) {
  if (!canAct) return null;
  if (i.action === "RECONNECT_WHATSAPP") {
    return <Link to="/settings?tab=whatsapp" className="btn btn-primary btn-sm">Reconectar WhatsApp →</Link>;
  }
  if (i.action === "CHECK_AI") {
    return <Link to="/settings?tab=bot" className="btn btn-ghost btn-sm">Ver o bot →</Link>;
  }
  return null;
}

/** Faixa no topo: o problema aberto mais importante (o da casa antes do da plataforma). */
export function HealthBanner({ canAct }: { canAct: boolean }) {
  const [open, setOpen] = useState<IncidentView[]>([]);

  useEffect(() => {
    let alive = true;
    const load = () =>
      api.get<IncidentView[]>("/api/admin/system-health/alerts")
        .then((r) => alive && setOpen(r))
        .catch(() => {});
    load();
    const t = window.setInterval(load, POLL_MS);
    return () => {
      alive = false;
      window.clearInterval(t);
    };
  }, []);

  if (open.length === 0) return null;
  const first = [...open].sort((a, b) => Number(a.platform) - Number(b.platform))[0];
  return (
    <div className={`health-strip${first.platform ? " platform" : ""}`} role="alert">
      <span>
        <b>⚠️ {first.message}</b>
        {open.length > 1 && <span className="faint"> · e mais {open.length - 1} alerta(s)</span>}
      </span>
      <ActionLink i={first} canAct={canAct} />
    </div>
  );
}

/** Cartão completo: abertos + últimos 30 dias. */
export function SystemHealthCard({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error } = useApi<HealthOverview>(() => api.get("/api/admin/system-health"), [reloadKey]);

  return (
    <div className="card">
      <div className="card-head">
        <h3>Saúde do sistema</h3>
        {data && (
          <span className={`badge ${data.open.length ? "badge-amber" : "badge-green"}`}>
            {data.open.length ? `${data.open.length} alerta(s)` : "tudo funcionando"}
          </span>
        )}
      </div>
      <div className="card-pad stack">
        {loading && !data ? (
          <SkeletonRows rows={2} />
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : data ? (
          <>
            {!data.monitoringEnabled && (
              <p className="muted" style={{ margin: 0 }}>O monitoramento automático está desligado neste servidor.</p>
            )}
            <p className="muted" style={{ margin: 0, fontSize: 14 }}>
              O sistema confere a cada 2 minutos o WhatsApp, a inteligência artificial, o envio de mensagens e a fila
              de lembretes. Quando algo sai do ar aparece aqui e numa faixa no topo do painel.
            </p>
            {data.open.length > 0 && (
              <ul className="health-list">
                {data.open.map((i) => (
                  <li key={i.id}>
                    <div className="row wrap" style={{ justifyContent: "space-between", gap: 8 }}>
                      <b>{i.message}</b>
                      <ActionLink i={i} canAct />
                    </div>
                    <div className="meta">
                      {i.platform ? "Plataforma · " : ""}desde {when(i.firstSeenAt)} · última vez {when(i.lastSeenAt)}
                      {i.occurrences > 1 ? ` · ${i.occurrences} ocorrências` : ""}
                      {i.detail ? ` · ${i.detail}` : ""}
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <details>
              <summary className="muted" style={{ cursor: "pointer", fontSize: 13.5 }}>
                Histórico (30 dias) — {data.history.length} resolvido(s)
              </summary>
              {data.history.length === 0 ? (
                <p className="muted" style={{ fontSize: 13 }}>Nenhum problema nos últimos 30 dias.</p>
              ) : (
                <ul className="health-list" style={{ marginTop: 10 }}>
                  {data.history.map((i) => (
                    <li key={i.id}>
                      <div>{i.message}</div>
                      <div className="meta">
                        {when(i.firstSeenAt)} → resolvido {when(i.resolvedAt)}
                        {i.occurrences > 1 ? ` · ${i.occurrences} ocorrências` : ""}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </details>
          </>
        ) : null}
      </div>
    </div>
  );
}
