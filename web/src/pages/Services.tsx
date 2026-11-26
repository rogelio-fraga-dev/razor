import { useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { ServiceOffering } from "../api/types";
import { money } from "../lib/format";
import { useAuth } from "../auth/AuthContext";
import { canEditCatalog } from "../lib/access";

interface Draft {
  id: string | null;
  name: string;
  price: string;
  durationMinutes: string;
  active: boolean;
  /** Vazio = vale o % de cada barbeiro. */
  commission: string;
}

const EMPTY: Draft = { id: null, name: "", price: "", durationMinutes: "30", active: true, commission: "" };

export function Services() {
  const { data, loading, error, reload } = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const toast = useToast();
  // A recepção lê o catálogo (a comanda precisa dele), mas preço e duração são do dono — a API
  // recusa a escrita, então a tela nem oferece.
  const { session } = useAuth();
  const canEdit = canEditCatalog(session?.role);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setFormError(null);
    setDraft({ ...EMPTY });
  };
  const openEdit = (s: ServiceOffering) => {
    setFormError(null);
    setDraft({ id: s.id, name: s.name, price: String(s.price), durationMinutes: String(s.durationMinutes), active: s.active,
      commission: s.commissionPercent == null ? "" : String(s.commissionPercent) });
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setFormError(null);
    const payload = {
      name: draft.name.trim(),
      price: Number(draft.price.replace(",", ".")),
      durationMinutes: Number(draft.durationMinutes),
      active: draft.active,
      commissionPercent: draft.commission.trim() === "" ? null : Number(draft.commission.replace(",", ".")),
    };
    if (payload.commissionPercent != null && (isNaN(payload.commissionPercent) || payload.commissionPercent < 0 || payload.commissionPercent > 100)) {
      setSaving(false);
      setFormError("A comissão precisa ficar entre 0 e 100%.");
      return;
    }
    try {
      if (draft.id) await api.put(`/api/admin/services/${draft.id}`, payload);
      else await api.post("/api/admin/services", payload);
      toast.success(draft.id ? "Serviço atualizado ✓" : `Serviço "${payload.name}" criado ✓`);
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
      title="Serviços"
      actions={canEdit ? <button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo serviço</button> : undefined}
    >
      <div className="card">
        <div className="card-head">
          <h3>Catálogo</h3>
          {data && <span className="badge badge-muted">{data.length} serviços</span>}
        </div>
        {loading ? (
          <SkeletonRows rows={4} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="✂"
            title="Nenhum serviço no catálogo."
            hint={canEdit ? "A IA usa esses preços e durações para agendar — cadastre o primeiro." : "Peça ao dono para cadastrar os serviços."}
            action={canEdit ? <button className="btn btn-primary" onClick={openNew}>+ Criar serviço</button> : undefined}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table table-cards">
              <thead>
                <tr>
                  <th>Serviço</th>
                  <th className="num">Preço</th>
                  <th>Duração</th>
                  <th>Comissão</th>
                  <th>Status</th>
                  {canEdit && <th></th>}
                </tr>
              </thead>
              <tbody>
                {data.map((s) => (
                  <tr key={s.id}>
                    <td className="tc-head" style={{ fontWeight: 550 }}>{s.name}</td>
                    <td className="num" data-label="Preço">{money(s.price)}</td>
                    <td className="muted" data-label="Duração">{s.durationMinutes} min</td>
                    <td data-label="Comissão">
                      {s.commissionPercent != null
                        ? <span className="badge badge-plan">{String(s.commissionPercent).replace(".", ",")}% próprio</span>
                        : <span className="faint">do barbeiro</span>}
                    </td>
                    <td data-label="Status">
                      {s.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Inativo</span>}
                    </td>
                    {canEdit && (
                      <td className="right tc-actions">
                        <button className="btn btn-ghost btn-sm" onClick={() => openEdit(s)}>Editar</button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {canEdit && draft && (
        <Modal
          title={draft.id ? "Editar serviço" : "Novo serviço"}
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
              <label>Nome
              <input className="input" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </label>
            </div>
            <div className="form-grid">
              <div className="field">
                <label>Preço (R$)
                <input className="input" inputMode="decimal" value={draft.price} onChange={(e) => setDraft({ ...draft, price: e.target.value })} />
            </label>
              </div>
              <div className="field">
                <label>Duração (min)
                <input className="input" inputMode="numeric" value={draft.durationMinutes} onChange={(e) => setDraft({ ...draft, durationMinutes: e.target.value })} />
            </label>
              </div>
            </div>
            <div className="field">
              <label>Comissão deste serviço (%)
              <input className="input" inputMode="decimal" placeholder="Vazio = usa o % de cada barbeiro"
                value={draft.commission} onChange={(e) => setDraft({ ...draft, commission: e.target.value })} />
              </label>
              <span className="faint" style={{ fontSize: 13 }}>
                Ex.: barba 50% e corte 40%. Preenchido, vale para todos os barbeiros neste serviço.
              </span>
            </div>
            {draft.id && <Switch checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="Serviço ativo" />}
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
