import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Customer, SubscriptionPlan } from "../api/types";

function dateShort(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("pt-BR");
}

export function Customers() {
  const { data, loading, error, reload } = useApi<Customer[]>(() => api.get("/api/admin/customers"), []);
  const plans = useApi<SubscriptionPlan[]>(() => api.get("/api/admin/plans"), []);
  const [q, setQ] = useState("");
  const [savingId, setSavingId] = useState<string | null>(null);
  const [assignError, setAssignError] = useState<string | null>(null);

  // Só planos ativos podem ser atribuídos; um plano inativo já vinculado ainda aparece como badge.
  const activePlans = useMemo(() => (plans.data ?? []).filter((p) => p.active), [plans.data]);

  const filtered = useMemo(() => {
    if (!data) return [];
    const term = q.trim().toLowerCase();
    if (!term) return data;
    return data.filter(
      (c) => (c.name ?? "").toLowerCase().includes(term) || c.phone.includes(term),
    );
  }, [data, q]);

  const assignPlan = async (customer: Customer, planId: string) => {
    setSavingId(customer.id);
    setAssignError(null);
    try {
      await api.put(`/api/admin/customers/${customer.id}/plan`, { planId: planId || null });
      reload();
    } catch (e) {
      setAssignError(e instanceof ApiError ? e.message : "Erro ao atualizar o plano.");
    } finally {
      setSavingId(null);
    }
  };

  const subscribers = data?.filter((c) => c.planId).length ?? 0;

  return (
    <Layout
      title="Clientes"
      actions={
        <input
          className="input"
          placeholder="Buscar por nome ou telefone"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{ width: 240 }}
        />
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Base de clientes</h3>
          {data && (
            <span className="badge badge-muted">
              {data.length} clientes · {subscribers} assinantes
            </span>
          )}
        </div>
        {assignError && <div className="error" style={{ marginBottom: 12 }}>{assignError}</div>}
        {loading ? (
          <SkeletonRows rows={5} />
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : filtered.length === 0 ? (
          data && data.length > 0 ? (
            <EmptyState icon="🔎" title="Nenhum cliente encontrado." hint="Tente outro nome ou telefone." />
          ) : (
            <EmptyState
              icon="👥"
              title="Sua base ainda está vazia."
              hint={
                <>
                  Quem falar com o bot no WhatsApp entra aqui sozinho — ou importe sua base atual
                  em CSV pelo WhatsApp do admin.
                </>
              }
              action={<Link className="btn btn-ghost" to="/settings">Conectar o WhatsApp</Link>}
            />
          )
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Telefone</th>
                  <th>Plano</th>
                  <th>Fidelidade</th>
                  <th>Último atendimento</th>
                  <th>Cadastro</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((c) => (
                  <tr key={c.id}>
                    <td style={{ fontWeight: 550 }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                        {c.name || "—"}
                        {c.planName && <span className="badge badge-plan">💎 {c.planName}</span>}
                      </span>
                    </td>
                    <td className="muted">{c.phone}</td>
                    <td>
                      <select
                        className="input"
                        style={{ maxWidth: 180 }}
                        value={c.planId ?? ""}
                        disabled={savingId === c.id || plans.loading}
                        onChange={(e) => assignPlan(c, e.target.value)}
                      >
                        <option value="">Avulso (sem plano)</option>
                        {/* Mantém o plano atual visível mesmo se inativo. */}
                        {c.planId && !activePlans.some((p) => p.id === c.planId) && c.planName && (
                          <option value={c.planId}>{c.planName} (inativo)</option>
                        )}
                        {activePlans.map((p) => (
                          <option key={p.id} value={p.id}>{p.name}</option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <span className="badge badge-muted">{c.fidelityPoints} / 10 pts</span>
                    </td>
                    <td className="muted">{dateShort(c.lastServiceDate)}</td>
                    <td className="muted">{dateShort(c.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Layout>
  );
}
