// Catálogo de produtos do balcão (V20): pomada, bebida, cera — o que a barbearia vende
// junto do corte. Espelha a tela de Serviços de propósito; a diferença é que produto não
// tem duração (não ocupa cadeira). Desativar não apaga: venda antiga precisa da referência.
import { useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { SellProductModal } from "../components/SellProductModal";
import { StockModal } from "../components/StockModal";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Product, ProductSale } from "../api/types";
import { money } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  price: string;
  active: boolean;
}

const EMPTY: Draft = { id: null, name: "", price: "", active: true };

const TIME_FMT = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

export function Products() {
  const { data, loading, error, reload } = useApi<Product[]>(() => api.get("/api/admin/products"), []);
  const toast = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [selling, setSelling] = useState<Product | null>(null);
  const [stockOf, setStockOf] = useState<Product | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [salesVersion, setSalesVersion] = useState(0);

  const sales = useApi<ProductSale[]>(() => api.get("/api/admin/product-sales"), [salesVersion]);

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
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="🧴"
            title="Nenhum produto no catálogo."
            hint="Cadastre o que você vende no balcão — pomada, bebida, cera. Depois dá para registrar a venda junto do atendimento."
            action={<button className="btn btn-primary" onClick={openNew}>+ Criar produto</button>}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table table-cards">
              <thead>
                <tr>
                  <th>Produto</th>
                  <th className="num">Preço</th>
                  <th>Estoque</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((p) => (
                  <tr key={p.id}>
                    <td className="tc-head" style={{ fontWeight: 550 }}>{p.name}</td>
                    <td className="num" data-label="Preço">{money(p.price)}</td>
                    <td data-label="Estoque">
                      {p.trackStock ? (
                        <>
                          <span className="mono">{p.stockQuantity}</span>
                          {p.lowStock && <span className="badge badge-amber" style={{ marginLeft: 8 }}>Repor</span>}
                        </>
                      ) : (
                        <span className="faint">não controlado</span>
                      )}
                    </td>
                    <td data-label="Status">
                      {p.active ? (
                        <span className="badge badge-green">Ativo</span>
                      ) : (
                        <span className="badge badge-muted">Inativo</span>
                      )}
                    </td>
                    <td className="right tc-actions">
                      {p.active && (
                        <button className="btn btn-primary btn-sm" onClick={() => setSelling(p)}>
                          Vender
                        </button>
                      )}
                      <button className="btn btn-ghost btn-sm" onClick={() => setStockOf(p)}>Estoque</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>Editar</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Vendas de hoje — a prova de que a venda entrou, sem sair da tela */}
      <div className="card">
        <div className="card-head">
          <h3>Vendas de hoje</h3>
          {sales.data && sales.data.length > 0 && (
            <span className="badge badge-green mono">
              {money(sales.data.reduce((sum, s) => sum + s.total, 0))}
            </span>
          )}
        </div>
        {sales.loading ? (
          <SkeletonRows rows={2} />
        ) : sales.error ? (
          <ErrorState message={sales.error} onRetry={sales.reload} />
        ) : !sales.data || sales.data.length === 0 ? (
          <EmptyState
            icon="🧾"
            title="Nenhuma venda de balcão hoje."
            hint="Use o botão “Vender” em um produto ativo. A venda entra no caixa aberto."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Hora</th>
                  <th>Produto</th>
                  <th>Qtde</th>
                  <th>Barbeiro</th>
                  <th>Pagamento</th>
                  <th style={{ textAlign: "right" }}>Total</th>
                </tr>
              </thead>
              <tbody>
                {sales.data.map((s) => (
                  <tr key={s.id}>
                    <td className="mono">{TIME_FMT.format(new Date(s.soldAt))}</td>
                    <td style={{ fontWeight: 550 }}>{s.productName}</td>
                    <td>{s.quantity}</td>
                    <td className="muted">{s.professionalName ?? "—"}</td>
                    <td className="muted">{s.paymentMethod}</td>
                    <td className="num">{money(s.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {stockOf && (
        <StockModal
          // Reencontra o produto na lista recarregada: sem isso o modal continuaria mostrando
          // o saldo de antes do movimento, enquanto o extrato logo abaixo já viria atualizado.
          product={data?.find((p) => p.id === stockOf.id) ?? stockOf}
          onClose={() => setStockOf(null)}
          onChanged={reload}
        />
      )}

      {selling && (
        <SellProductModal
          product={selling}
          onClose={() => setSelling(null)}
          onSold={() => {
            setSalesVersion((v) => v + 1);
            reload(); // a venda deu baixa no estoque
          }}
        />
      )}

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
              <label>Nome
              <input
                className="input"
                value={draft.name}
                placeholder="ex.: Pomada modeladora"
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </label>
            </div>
            <div className="field">
              <label>Preço (R$)
              <input
                className="input"
                inputMode="decimal"
                value={draft.price}
                placeholder="ex.: 30,00"
                onChange={(e) => setDraft({ ...draft, price: e.target.value })}
              />
            </label>
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
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
