import { useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { PlanBenefitsModal } from "../components/PlanBenefitsModal";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { SubscriptionPlan } from "../api/types";
import { money } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  price: string;
  description: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, name: "", price: "", description: "", active: true };

export function Plans() {
  const { data, loading, error, reload } = useApi<SubscriptionPlan[]>(() => api.get("/api/admin/plans"), []);
  const toast = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [benefitsOf, setBenefitsOf] = useState<SubscriptionPlan | null>(null);
  const [creatingSuggested, setCreatingSuggested] = useState(false);

  const openNew = () => {
    setFormError(null);
    setDraft({ ...EMPTY });
  };

  /** Ponto de partida pronto: Bronze/Prata/Ouro já com os benefícios montados. */
  const createSuggested = async () => {
    setCreatingSuggested(true);
    try {
      await api.post("/api/admin/plans/suggested");
      toast.success("Planos Bronze, Prata e Ouro criados ✓ Ajuste preços e benefícios como quiser.");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao criar os planos sugeridos.");
    } finally {
      setCreatingSuggested(false);
    }
  };
  const openEdit = (p: SubscriptionPlan) => {
    setFormError(null);
    setDraft({ id: p.id, name: p.name, price: String(p.price), description: p.description ?? "", active: p.active });
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setFormError(null);
    const payload = {
      name: draft.name.trim(),
      price: Number(draft.price.replace(",", ".")),
      description: draft.description.trim() || null,
      active: draft.active,
    };
    try {
      if (draft.id) await api.put(`/api/admin/plans/${draft.id}`, payload);
      else await api.post("/api/admin/plans", payload);
      toast.success(draft.id ? "Plano atualizado ✓" : `Plano "${payload.name}" criado ✓`);
      setDraft(null);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Layout
      title="Planos"
      actions={
        <div className="row" style={{ gap: 8 }}>
          {data && data.length === 0 && (
            <button
              className="btn btn-ghost btn-sm"
              onClick={createSuggested}
              disabled={creatingSuggested}
              title="Cria Bronze, Prata e Ouro já com os benefícios configurados"
            >
              {creatingSuggested ? "Criando…" : "✨ Usar planos sugeridos"}
            </button>
          )}
          <button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo plano</button>
        </div>
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Clube de assinatura</h3>
          {data && <span className="badge badge-muted">{data.length} planos</span>}
        </div>
        {loading ? (
          <SkeletonRows rows={3} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="💎"
            title="Nenhum plano de assinatura ainda."
            hint="Crie o clube da sua barbearia (ex.: VIP mensal) e vincule clientes na tela Clientes — receita recorrente todo mês."
            action={<button className="btn btn-primary" onClick={openNew}>+ Criar plano</button>}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Plano</th>
                  <th>Preço/mês</th>
                  <th>Descrição</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 550 }}>{p.name}</td>
                    <td className="num">{money(p.price)}</td>
                    <td className="muted">{p.description || "—"}</td>
                    <td>
                      {p.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Inativo</span>}
                    </td>
                    <td className="right">
                      <button className="btn btn-ghost btn-sm" onClick={() => setBenefitsOf(p)}>Benefícios</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>Editar</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {benefitsOf && (
        <PlanBenefitsModal plan={benefitsOf} onClose={() => setBenefitsOf(null)} />
      )}

      {draft && (
        <Modal
          title={draft.id ? "Editar plano" : "Novo plano"}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={save} disabled={saving || !draft.name.trim()}>
                {saving ? "Salvando…" : "Salvar"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Nome do plano
              <input className="input" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="ex.: VIP" />
            </label>
            </div>
            <div className="field">
              <label>Preço mensal (R$)
              <input className="input" inputMode="decimal" value={draft.price} onChange={(e) => setDraft({ ...draft, price: e.target.value })} />
            </label>
            </div>
            <div className="field">
              <label>Descrição (opcional)
              <textarea
                className="input"
                rows={3}
                value={draft.description}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                style={{ resize: "vertical", fontFamily: "inherit" }}
              />
            </label>
            </div>
            {draft.id && <Switch checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="Plano ativo" />}
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
