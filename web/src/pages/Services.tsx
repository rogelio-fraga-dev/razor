import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { ServiceOffering } from "../api/types";
import { money } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  price: string;
  durationMinutes: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, name: "", price: "", durationMinutes: "30", active: true };

export function Services() {
  const { data, loading, error, reload } = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setFormError(null);
    setDraft({ ...EMPTY });
  };
  const openEdit = (s: ServiceOffering) => {
    setFormError(null);
    setDraft({ id: s.id, name: s.name, price: String(s.price), durationMinutes: String(s.durationMinutes), active: s.active });
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
    };
    try {
      if (draft.id) await api.put(`/api/admin/services/${draft.id}`, payload);
      else await api.post("/api/admin/services", payload);
      setDraft(null);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Layout title="Serviços" actions={<button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo serviço</button>}>
      <div className="card">
        <div className="card-head">
          <h3>Catálogo</h3>
          {data && <span className="badge badge-muted">{data.length} serviços</span>}
        </div>
        {loading ? (
          <div className="empty">Carregando…</div>
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : !data || data.length === 0 ? (
          <div className="empty">Nenhum serviço cadastrado. Crie o primeiro.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Serviço</th>
                  <th>Preço</th>
                  <th>Duração</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 550 }}>{s.name}</td>
                    <td>{money(s.price)}</td>
                    <td className="muted">{s.durationMinutes} min</td>
                    <td>
                      {s.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Inativo</span>}
                    </td>
                    <td className="right">
                      <button className="btn btn-ghost btn-sm" onClick={() => openEdit(s)}>Editar</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {draft && (
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
              <label>Nome</label>
              <input className="input" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </div>
            <div className="form-grid">
              <div className="field">
                <label>Preço (R$)</label>
                <input className="input" inputMode="decimal" value={draft.price} onChange={(e) => setDraft({ ...draft, price: e.target.value })} />
              </div>
              <div className="field">
                <label>Duração (min)</label>
                <input className="input" inputMode="numeric" value={draft.durationMinutes} onChange={(e) => setDraft({ ...draft, durationMinutes: e.target.value })} />
              </div>
            </div>
            {draft.id && <Switch checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="Serviço ativo" />}
            {formError && <div className="error">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
