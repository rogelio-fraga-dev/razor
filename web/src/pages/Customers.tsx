import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { Pagination } from "../components/Pagination";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Customer, CustomerPackage, CustomerSummary, PageMeta, SubscriptionPlan } from "../api/types";

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

/** Espelha CustomerService.CustomerFilter no backend. */
type Filter = "ALL" | "SUBSCRIBERS" | "NEW";

/** Linhas por página. Menor que o padrão de 20: a linha de cliente é alta (avatar, duas
 *  linhas de texto e um seletor), e 20 delas não cabem numa tela sem rolar. */
const PAGE_SIZE = 12;

const FILTERS: { id: Filter; label: string }[] = [
  { id: "ALL", label: "Todos" },
  { id: "SUBSCRIBERS", label: "💎 Assinantes" },
  { id: "NEW", label: "Novos (30d)" },
];

export function Customers() {
  const toast = useToast();
  // Vincular plano e so do dono (SecurityConfig: PUT /customers/*/plan = OWNER). A recepcao ve o
  // plano do cliente, mas sem o seletor — e sem buscar /plans, que tambem e so do dono.
  const { session } = useAuth();
  const canEditPlan = session?.role === "OWNER";
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const [page, setPage] = useState(0);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  // A busca vai ao servidor, então cada tecla não pode virar uma requisição. 350ms é o
  // intervalo em que a digitação já parou mas a espera ainda não incomoda.
  const [debouncedQ, setDebouncedQ] = useState("");
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQ(q.trim()), 350);
    return () => clearTimeout(id);
  }, [q]);

  // Buscar ou trocar de filtro tem que voltar para a primeira página: continuar na 4 depois de
  // filtrar mostraria uma lista vazia com resultados existindo.
  useEffect(() => {
    setPage(0);
  }, [debouncedQ, filter]);

  const query = useMemo(() => {
    const p = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), filter });
    if (debouncedQ) p.set("q", debouncedQ);
    return p.toString();
  }, [page, filter, debouncedQ]);

  // Sem `reload`: recarregar é feito bumpando `version`, que também refaz os totais do topo —
  // vincular um plano muda o contador de assinantes, e recarregar só a lista o deixaria velho.
  const { data, loading, error } = useApi<PageMeta<Customer>>(
    () => api.get(`/api/admin/customers?${query}`),
    [query, version],
  );
  const summary = useApi<CustomerSummary>(() => api.get("/api/admin/customers/summary"), [version]);
  const plans = useApi<SubscriptionPlan[] | null>(
    () => (canEditPlan ? api.get("/api/admin/plans") : Promise.resolve(null)),
    [canEditPlan],
  );

  // Saldo de pacotes pré-pagos (V34): quem tem corte pago aparece marcado na lista.
  const packages = useApi<CustomerPackage[]>(() => api.get("/api/admin/customers/package-balances"), [version]);
  const packagesByCustomer = useMemo(() => {
    const m = new Map<string, CustomerPackage[]>();
    for (const p of packages.data ?? []) m.set(p.customerId, [...(m.get(p.customerId) ?? []), p]);
    return m;
  }, [packages.data]);

  const activePlans = useMemo(() => (plans.data ?? []).filter((p) => p.active), [plans.data]);

  const assignPlan = async (customer: Customer, planId: string) => {
    setSavingId(customer.id);
    try {
      await api.put(`/api/admin/customers/${customer.id}/plan`, { planId: planId || null });
      toast.success(planId ? "Plano vinculado ✓" : "Cliente voltou a ser avulso ✓");
      // Recarrega a página atual e os totais: vincular plano muda o contador de assinantes.
      setVersion((v) => v + 1);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao atualizar o plano.");
    } finally {
      setSavingId(null);
    }
  };

  const buscando = debouncedQ.length > 0 || filter !== "ALL";
  const items = data?.items ?? [];

  return (
    <Layout flush title="Clientes">
      <div className="page-panel readable">
        <div className="list-toolbar">
          <input
            className="input"
            placeholder="Buscar por nome ou telefone…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            style={{ maxWidth: 280 }}
            aria-label="Buscar clientes"
          />
          <div className="chip-row">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                className={`chip-btn ${filter === f.id ? "active" : ""}`}
                onClick={() => setFilter(f.id)}
              >
                {f.label}
              </button>
            ))}
          </div>
          {/* Os totais vêm do servidor, contados sobre a base inteira. Deduzir de uma página
              daria "3 assinantes" numa base com 200 — número errado é pior que número nenhum. */}
          <span className="list-count">
            {data ? `${data.total} ${buscando ? "encontrado(s)" : "cliente(s)"}` : "…"}
            {summary.data && ` · ${summary.data.subscribers} assinantes`}
          </span>
        </div>

        {loading && !data ? (
          <SkeletonRows rows={6} />
        ) : error ? (
          <div className="empty error" role="alert">{error}</div>
        ) : items.length === 0 ? (
          buscando ? (
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
          <>
            <div className="rowlist">
              {items.map((c) => (
                <article className="rowitem" key={c.id}>
                  <span className="rw-avatar" aria-hidden="true">
                    {(c.name || "?").charAt(0).toUpperCase()}
                  </span>

                  <div className="rw-main">
                    <span className="rw-title">
                      {c.name || "Sem nome"}
                      {c.planName && <span className="badge badge-plan">💎 {c.planName}</span>}
                      {(packagesByCustomer.get(c.id) ?? []).map((p) => (
                        <span
                          key={p.id}
                          className="badge badge-green"
                          title={`${p.packageName} · ${p.serviceName ?? "qualquer serviço"} · vence ${new Date(p.expiresAt + "T00:00:00").toLocaleDateString("pt-BR")}`}
                        >
                          🎫 {p.remainingUses}/{p.totalUses} {p.packageName}
                        </span>
                      ))}
                    </span>
                    <span className="rw-meta">
                      <span className="mono">{phoneBr(c.phone)}</span>
                      <span>· {c.fidelityPoints}/10 pts</span>
                      <span>· última visita {dateShort(c.lastServiceDate)}</span>
                    </span>
                  </div>

                  <div className="rw-side">
                    <div className="rw-actions">
                      {canEditPlan ? (
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
                      ) : (
                        <span className="faint" style={{ fontSize: 13 }}>{c.planName ?? "Avulso"}</span>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
            {data && <Pagination page={data} onChange={setPage} label="clientes" busy={loading} />}
          </>
        )}
      </div>
    </Layout>
  );
}
