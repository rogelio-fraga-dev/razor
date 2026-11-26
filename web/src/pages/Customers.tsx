import { usePlanFeatures } from "../components/Layout";
import { featureEnabled } from "../lib/nav";
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { ErrorState } from "../components/ErrorState";
import { Link } from "react-router";
import { Layout } from "../components/Layout";
import { Drawer } from "../components/Drawer";
import { EmptyState } from "../components/EmptyState";
import { Pagination } from "../components/Pagination";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import { BroadcastModal, CustomerTagsModal, STATUS_META, TagChip } from "../components/CustomerTags";
import type {
  Customer,
  CustomerPackage,
  CustomerStatus,
  CustomerSummary,
  CustomerTag,
  PageMeta,
  SubscriptionPlan,
} from "../api/types";

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

const STATUSES: CustomerStatus[] = ["NOVO", "ATIVO", "EM_RISCO", "SUMIDO"];

const FILTERS: { id: Filter; label: string }[] = [
  { id: "ALL", label: "Todos" },
  { id: "SUBSCRIBERS", label: "💎 Assinantes" },
  { id: "NEW", label: "Novos (30d)" },
];

export function Customers() {
  usePlanFeatures(true);
  const loyalty = featureEnabled("LOYALTY"); // piloto: sem cartão fidelidade
  const toast = useToast();
  // Vincular plano e so do dono (SecurityConfig: PUT /customers/*/plan = OWNER). A recepcao ve o
  // plano do cliente, mas sem o seletor — e sem buscar /plans, que tambem e so do dono.
  const { session } = useAuth();
  const canEditPlan = session?.role === "OWNER";
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const [status, setStatus] = useState<CustomerStatus | null>(null);
  const [tagId, setTagId] = useState<string>("");
  const [tagsFor, setTagsFor] = useState<Customer | null>(null);
  // Gaveta guarda só o id: depois de salvar (plano, tags) a lista recarrega e a gaveta mostra
  // o cliente atualizado, não uma cópia velha.
  const [detailId, setDetailId] = useState<string | null>(null);
  // Segmento congelado no momento em que o envio foi aberto: o que vai para o backend são os
  // filtros CRUS (tagId/status), nunca algo derivado da lista de tags (que pode estar carregando).
  const [broadcast, setBroadcast] = useState<{ tagId: string | null; status: CustomerStatus | null } | null>(null);
  const [recipients, setRecipients] = useState<number | null>(null);
  const [recipientsFailed, setRecipientsFailed] = useState(false);
  const previewSeq = useRef(0);
  const [tagsVersion, setTagsVersion] = useState(0);
  // Disparo para a base é decisão do dono (o backend recusa os outros papéis com 403).
  const canBroadcast = session?.role === "OWNER";
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
  }, [debouncedQ, filter, status, tagId]);

  const query = useMemo(() => {
    const p = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), filter });
    if (debouncedQ) p.set("q", debouncedQ);
    if (status) p.set("status", status);
    if (tagId) p.set("tagId", tagId);
    return p.toString();
  }, [page, filter, debouncedQ, status, tagId]);

  // Sem `reload`: recarregar é feito bumpando `version`, que também refaz os totais do topo —
  // vincular um plano muda o contador de assinantes, e recarregar só a lista o deixaria velho.
  const { data, loading, error, reload } = useApi<PageMeta<Customer>>(
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

  const tags = useApi<CustomerTag[]>(() => api.get("/api/admin/customers/tags"), [tagsVersion]);

  const openBroadcast = async () => {
    const seq = ++previewSeq.current;
    const segment = { tagId: tagId || null, status };
    setRecipients(null);
    setRecipientsFailed(false);
    setBroadcast(segment);
    try {
      const p = new URLSearchParams();
      if (segment.status) p.set("status", segment.status);
      if (segment.tagId) p.set("tagId", segment.tagId);
      const res = await api.get<{ recipients: number }>(`/api/admin/broadcasts/preview?${p.toString()}`);
      if (seq === previewSeq.current) setRecipients(res.recipients);
    } catch {
      if (seq === previewSeq.current) setRecipientsFailed(true);
    }
  };

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

  const buscando = debouncedQ.length > 0 || filter !== "ALL" || status !== null || tagId !== "";
  const segmentado = status !== null || tagId !== "";
  const items = data?.items ?? [];
  const detail = detailId ? items.find((c) => c.id === detailId) ?? null : null;

  const planSelect = (c: Customer, style?: CSSProperties) => (
    <select
      className="input"
      style={style}
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
  );

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
          <div className="chip-row" role="group" aria-label="Filtrar por status">
            {STATUSES.map((s) => (
              <button
                key={s}
                className={`chip-btn ${status === s ? "active" : ""}`}
                title={STATUS_META[s].hint}
                aria-pressed={status === s}
                onClick={() => setStatus(status === s ? null : s)}
              >
                {STATUS_META[s].label}
              </button>
            ))}
          </div>
          {(tags.data?.length ?? 0) > 0 && (
            <select
              className="input"
              style={{ maxWidth: 170, padding: "7px 10px", fontSize: 13 }}
              value={tagId}
              onChange={(e) => setTagId(e.target.value)}
              aria-label="Filtrar por tag"
            >
              <option value="">Todas as tags</option>
              {(tags.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>{t.name}</option>
              ))}
            </select>
          )}
          {canBroadcast && segmentado && (
            <button className="btn btn-ghost" onClick={openBroadcast}>
              📢 Enviar mensagem para este grupo
            </button>
          )}
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
          <ErrorState message={error} onRetry={reload} />
        ) : items.length === 0 ? (
          buscando ? (
            <EmptyState
              icon="🔎"
              title="Nenhum cliente encontrado."
              hint="Tente outro nome, telefone ou filtro."
              action={
                <button
                  className="btn btn-ghost"
                  onClick={() => {
                    setQ("");
                    setFilter("ALL");
                    setStatus(null);
                    setTagId("");
                  }}
                >
                  Limpar busca e filtros
                </button>
              }
            />
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
                  {/* Linha inteira abre a gaveta do cliente (no celular é o único caminho: as
                      ações da direita somem e moram na gaveta). */}
                  <button
                    type="button"
                    className="rw-open"
                    onClick={() => setDetailId(c.id)}
                    aria-label={`Abrir ${c.name || "cliente sem nome"}`}
                  >
                  <span className="rw-avatar" aria-hidden="true">
                    {(c.name || "?").charAt(0).toUpperCase()}
                  </span>

                  <div className="rw-main">
                    <span className="rw-title">
                      {c.name || "Sem nome"}
                      {c.planName && <span className="badge badge-plan">💎 {c.planName}</span>}
                      {c.status && (
                        <span className={`badge ${STATUS_META[c.status].className}`} title={STATUS_META[c.status].hint}>
                          {STATUS_META[c.status].label}
                        </span>
                      )}
                      {(c.tags ?? []).map((t) => <TagChip key={t.id} tag={t} />)}
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
                      {loyalty && <span>· {c.fidelityPoints}/10 pts</span>}
                      <span>· última visita {dateShort(c.lastServiceDate)}</span>
                    </span>
                  </div>
                  <span className="rw-chevron" aria-hidden="true">›</span>
                  </button>

                  <div className="rw-side">
                    <div className="rw-actions">
                      <button
                        className="btn btn-ghost"
                        style={{ padding: "7px 10px", fontSize: 13 }}
                        onClick={() => setTagsFor(c)}
                        aria-label={`Tags de ${c.name ?? "cliente"}`}
                      >
                        🏷️ Tags
                      </button>
                      {canEditPlan ? (
                        planSelect(c, { maxWidth: 170, padding: "7px 10px", fontSize: 13 })
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

      {detail && (
        <Drawer
          title={detail.name || "Sem nome"}
          subtitle={<span className="mono">{phoneBr(detail.phone)}</span>}
          onClose={() => setDetailId(null)}
        >
          <div className="cust-actions">
            <a className="btn btn-primary" href={`https://wa.me/${detail.phone.replace(/\D/g, "")}`} target="_blank" rel="noreferrer">
              WhatsApp
            </a>
            <a className="btn btn-ghost" href={`tel:+${detail.phone.replace(/\D/g, "")}`}>
              Ligar
            </a>
            <button className="btn btn-ghost" onClick={() => setTagsFor(detail)}>
              🏷️ Tags
            </button>
          </div>
          <dl className="ag-dl">
            <dt>Situação</dt>
            <dd>
              {detail.status ? (
                <span className={`badge ${STATUS_META[detail.status].className}`}>{STATUS_META[detail.status].label}</span>
              ) : (
                "—"
              )}
            </dd>
            <dt>Última visita</dt>
            <dd>{dateShort(detail.lastServiceDate)}</dd>
            {featureEnabled("LOYALTY") && (<><dt>Fidelidade</dt>
            <dd>{detail.fidelityPoints}/10 pontos</dd></>)}
            <dt>Cliente desde</dt>
            <dd>{dateShort(detail.createdAt)}</dd>
          </dl>
          {(detail.tags ?? []).length > 0 && (
            <div className="cust-badges">
              {detail.tags.map((t) => <TagChip key={t.id} tag={t} />)}
            </div>
          )}
          <div className="stack" style={{ gap: 6 }}>
            <span className="label" style={{ fontSize: 13, color: "var(--text-muted)" }}>Plano</span>
            {canEditPlan ? planSelect(detail, { minHeight: 44 }) : <span>{detail.planName ?? "Avulso"}</span>}
          </div>
          {(packagesByCustomer.get(detail.id) ?? []).length > 0 && (
            <div className="stack" style={{ gap: 6 }}>
              <span className="label" style={{ fontSize: 13, color: "var(--text-muted)" }}>Pacotes</span>
              {(packagesByCustomer.get(detail.id) ?? []).map((p) => (
                <span key={p.id} className="badge badge-green" style={{ alignSelf: "flex-start" }}>
                  🎫 {p.remainingUses}/{p.totalUses} {p.packageName} · vence{" "}
                  {new Date(p.expiresAt + "T00:00:00").toLocaleDateString("pt-BR")}
                </span>
              ))}
            </div>
          )}
        </Drawer>
      )}
      {tagsFor && (
        <CustomerTagsModal
          customer={tagsFor}
          tags={tags.data ?? []}
          onClose={() => setTagsFor(null)}
          onSaved={() => setVersion((v) => v + 1)}
          onTagsChanged={() => setTagsVersion((v) => v + 1)}
        />
      )}
      {broadcast && (
        <BroadcastModal
          tagId={broadcast.tagId}
          tagName={(tags.data ?? []).find((t) => t.id === broadcast.tagId)?.name ?? null}
          status={broadcast.status}
          recipients={recipients}
          previewFailed={recipientsFailed}
          onClose={() => {
            previewSeq.current++;
            setBroadcast(null);
          }}
        />
      )}
    </Layout>
  );
}
