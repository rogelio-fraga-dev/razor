// Catálogo de produtos do balcão (V20): pomada, bebida, cera — o que a barbearia vende
// junto do corte. Espelha a tela de Serviços de propósito; a diferença é que produto não
// tem duração (não ocupa cadeira). Desativar não apaga: venda antiga precisa da referência.
import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Product } from "../api/types";
import { money } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  price: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, name: "", price: "", active: true };

export function Products() {
  const { data, loading, error, reload } = useApi<Product[]>(() => api.get("/api/admin/products"), []);
  const toast = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const openNew = () => {
    setFormError(null);
    setDraft({ ...EMPTY });
  };
  const openEdit = (p: Product) => {
    setFormError(null);
    setDraft({ id: p.id, name: p.name, price: String(p.price), active: p.active });
  };

  const save = async () => {
    if (!draft) return;
    const price = Number(draft.price.replace(",", "."));
    if (!Number.isFinite(price) || price <= 0) {
      setFormError("O preço do produto deve ser maior que zero.");
      return;
    }
    setSaving(true);
    setFormError(null);
    const payload = { name: draft.name.trim(), price, active: draft.active };
    try {
      if (draft.id) await api.put(`/api/admin/products/${draft.id}`, payload);
      else await api.post("/api/admin/products", payload);
      toast.success(draft.id ? "Produto atualizado ✓" : `Produto "${payload.name}" criado ✓`);
      setDraft(null);
      reload();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const activeCount = data?.filter((p) => p.active).length ?? 0;

  return (
    <Layout
      title="Produtos"
      actions={<button className="btn btn-primary btn-sm" onClick={openNew}>+ Novo produto</button>}
    >
      <div className="card">
        <div className="card-head">
          <h3>Catálogo do balcão</h3>
          {data && data.length > 0 && (
            <span className="badge badge-muted">
              {activeCount} ativo(s) de {data.length}
            </span>
          )}
        </div>
        {loading ? (
          <SkeletonRows rows={4} />
        ) : error ? (
          <div className="empty error">{error}</div>
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="🧴"
            title="Nenhum produto no catálogo."
            hint="Cadastre o que você vende no balcão — pomada, bebida, cera. Depois dá para registrar a venda junto do atendimento."
            action={<button className="btn btn-primary" onClick={openNew}>+ Criar produto</button>}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Produto</th>
                  <th>Preço</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 550 }}>{p.name}</td>
                    <td>{money(p.price)}</td>
                    <td>
                      {p.active ? (
                        <span className="badge badge-green">Ativo</span>
                      ) : (
                        <span className="badge badge-muted">Inativo</span>
                      )}
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
          title={draft.id ? "Editar produto" : "Novo produto"}
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
              <input
                className="input"
                value={draft.name}
                placeholder="ex.: Pomada modeladora"
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </div>
            <div className="field">
              <label>Preço (R$)</label>
              <input
                className="input"
                inputMode="decimal"
                value={draft.price}
                placeholder="ex.: 30,00"
                onChange={(e) => setDraft({ ...draft, price: e.target.value })}
              />
            </div>
            {draft.id && (
              <Switch
                checked={draft.active}
                onChange={(v) => setDraft({ ...draft, active: v })}
                label="Produto ativo"
              />
            )}
            {draft.id && !draft.active && (
              <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
                Inativo some da hora de vender, mas continua nos relatórios — vendas antigas
                mantêm a referência.
              </p>
            )}
            {formError && <div className="error">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
