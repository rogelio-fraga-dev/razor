import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Professional } from "../api/types";
import { percent } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  phone: string;
  commissionPercent: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, name: "", phone: "", commissionPercent: "0", active: true };

export function Professionals() {
  const { data, loading, error, reload } = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setFormError(null);
    setDraft({ ...EMPTY });
  };
  const openEdit = (p: Professional) => {
    setFormError(null);
    setDraft({ id: p.id, name: p.name, phone: p.phone ?? "", commissionPercent: String(p.commissionPercent), active: p.active });
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setFormError(null);
    const payload = {
      name: draft.name.trim(),
      phone: draft.phone.trim() || null,
      commissionPercent: Number(draft.commissionPercent.replace(",", ".")),
      active: draft.active,
    };
    try {
      if (draft.id) await api.put(`/api/admin/professionals/${draft.id}`, payload);
      else await api.post("/api/admin/professionals", payload);
      setDraft(null);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Layout title="Profissionais" actions={<button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo barbeiro</button>}>
      <div className="card">
        <div className="card-head">
          <h3>Equipe</h3>
          {data && <span className="badge badge-muted">{data.length} profissionais</span>}
        </div>
        {loading ? (
          <div className="empty">Carregando…</div>
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : !data || data.length === 0 ? (
          <div className="empty">Nenhum barbeiro cadastrado.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Telefone</th>
                  <th>Comissão</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 550 }}>{p.name}</td>
                    <td className="muted">{p.phone || "—"}</td>
                    <td>{percent(p.commissionPercent)}</td>
                    <td>
                      {p.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Inativo</span>}
                    </td>
                    <td className="right">
                      <button className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>Editar</button>
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
          title={draft.id ? "Editar barbeiro" : "Novo barbeiro"}
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
                <label>Telefone (opcional)</label>
                <input className="input" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} />
              </div>
              <div className="field">
                <label>Comissão (%)</label>
                <input className="input" inputMode="decimal" value={draft.commissionPercent} onChange={(e) => setDraft({ ...draft, commissionPercent: e.target.value })} />
              </div>
            </div>
            {draft.id && <Switch checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="Barbeiro ativo" />}
            {formError && <div className="error">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
