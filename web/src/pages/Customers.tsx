import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Customer, SubscriptionPlan } from "../api/types";

function dateShort(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("pt-BR");
}

function phoneBr(p: string): string {
  const d = p.replace(/\D/g, "").replace(/^55/, "");
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return p;
}

type Filter = "all" | "subs" | "new";

export function Customers() {
  const { data, loading, error, reload } = useApi<Customer[]>(() => api.get("/api/admin/customers"), []);
  const plans = useApi<SubscriptionPlan[]>(() => api.get("/api/admin/plans"), []);
  const toast = useToast();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [savingId, setSavingId] = useState<string | null>(null);

  const activePlans = useMemo(() => (plans.data ?? []).filter((p) => p.active), [plans.data]);

  const filtered = useMemo(() => {
    let list = data ?? [];
    if (filter === "subs") list = list.filter((c) => c.planId);
    if (filter === "new") {
      const cut = Date.now() - 30 * 864e5;
      list = list.filter((c) => c.createdAt && new Date(c.createdAt).getTime() > cut);
    }
    const term = q.trim().toLowerCase();
    if (term) {
      list = list.filter((c) => (c.name ?? "").toLowerCase().includes(term) || c.phone.includes(term));
    }
    return list;
  }, [data, q, filter]);

  const assignPlan = async (customer: Customer, planId: string) => {
    setSavingId(customer.id);
    try {
      await api.put(`/api/admin/customers/${customer.id}/plan`, { planId: planId || null });
      toast.success(planId ? "Plano vinculado ✓" : "Cliente voltou a ser avulso ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao atualizar o plano.");
    } finally {
      setSavingId(null);
    }
  };

  const subscribers = data?.filter((c) => c.planId).length ?? 0;

  return (
    <Layout flush title="Clientes">
      <div className="page-panel">
        <div className="list-toolbar">
          <input
            className="input"
            placeholder="Buscar por nome ou telefone…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            style={{ maxWidth: 280 }}
          />
          <div className="chip-row">
            <button className={`chip-btn ${filter === "all" ? "active" : ""}`} onClick={() => setFilter("all")}>
              Todos
            </button>
            <button className={`chip-btn ${filter === "subs" ? "active" : ""}`} onClick={() => setFilter("subs")}>
              💎 Assinantes
            </button>
            <button className={`chip-btn ${filter === "new" ? "active" : ""}`} onClick={() => setFilter("new")}>
              Novos (30d)
            </button>
          </div>
          <span className="list-count">
            {filtered.length} de {data?.length ?? 0} · {subscribers} assinantes
          </span>
        </div>

        {loading ? (
          <SkeletonRows rows={6} />
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : filtered.length === 0 ? (
          data && data.length > 0 ? (
            <EmptyState icon="🔎" title="Nenhum cliente encontrado." hint="Tente outro nome, telefone ou filtro." />
          ) : (
            <EmptyState
              icon="👥"
              title="Sua base ainda está vazia."
              hint="Quem falar com o bot no WhatsApp entra aqui sozinho — ou importe sua base atual em CSV pelo WhatsApp do admin."
              action={<Link className="btn btn-ghost" to="/settings">Conectar o WhatsApp</Link>}
            />
          )
        ) : (
          <div className="rowlist">
            {filtered.map((c) => (
              <article className="rowitem" key={c.id}>
                <span className="rw-avatar" aria-hidden="true">
                  {(c.name || "?").charAt(0).toUpperCase()}
                </span>

                <div className="rw-main">
                  <span className="rw-title">
                    {c.name || "Sem nome"}
                    {c.planName && <span className="badge badge-plan">💎 {c.planName}</span>}
                  </span>
                  <span className="rw-meta">
                    <span className="mono">{phoneBr(c.phone)}</span>
                    <span>· {c.fidelityPoints}/10 pts</span>
                    <span>· última visita {dateShort(c.lastServiceDate)}</span>
                  </span>
                </div>

                <div className="rw-side">
                  <div className="rw-actions">
                    <select
                      className="input"
                      style={{ maxWidth: 170, padding: "7px 10px", fontSize: 13 }}
                      value={c.planId ?? ""}
                      disabled={savingId === c.id || plans.loading}
                      onChange={(e) => assignPlan(c, e.target.value)}
                      aria-label={`Plano de ${c.name ?? "cliente"}`}
                    >
                      <option value="">Avulso (sem plano)</option>
                      {c.planId && !activePlans.some((p) => p.id === c.planId) && c.planName && (
                        <option value={c.planId}>{c.planName} (inativo)</option>
                      )}
                      {activePlans.map((p) => (
                        <option key={p.id} value={p.id}>{p.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
}
