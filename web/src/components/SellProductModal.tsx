// Venda de balcão (V21). O preço não é editável aqui de propósito: quem manda é o catálogo
// no momento da venda. A venda exige caixa aberto — sem isso o dinheiro não apareceria na
// conferência da gaveta no fechamento.
import { useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { CashPaymentMethod, Product, Professional } from "../api/types";
import { money } from "../lib/format";

const PAYMENT_METHODS: { value: CashPaymentMethod; label: string }[] = [
  { value: "DINHEIRO", label: "Dinheiro" },
  { value: "PIX", label: "PIX" },
  { value: "DEBITO", label: "Débito" },
  { value: "CREDITO", label: "Crédito" },
];

export function SellProductModal({
  product,
  onClose,
  onSold,
}: {
  product: Product;
  onClose: () => void;
  onSold: () => void;
}) {
  const toast = useToast();
  const professionals = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);
  const [quantity, setQuantity] = useState("1");
  const [professionalId, setProfessionalId] = useState("");
  const [method, setMethod] = useState<CashPaymentMethod>("DINHEIRO");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const qty = Number(quantity);
  const validQty = Number.isInteger(qty) && qty > 0;
  const total = validQty ? product.price * qty : 0;

  const sell = async () => {
    if (!validQty) {
      setError("A quantidade deve ser um número inteiro maior que zero.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api.post("/api/admin/product-sales", {
        productId: product.id,
        quantity: qty,
        professionalId: professionalId || null,
        paymentMethod: method,
      });
      toast.success(`Venda registrada ✓ ${money(total)} no caixa.`);
      onSold();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao registrar a venda.");
    } finally {
      setSaving(false);
    }
  };

  const activeProfessionals = (professionals.data ?? []).filter((p) => p.active);

  return (
    <Modal
      title={`Vender ${product.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Cancelar</button>
          <button className="btn btn-primary" onClick={sell} disabled={saving || !validQty}>
            {saving ? "Registrando…" : `Vender ${money(total)}`}
          </button>
        </>
      }
    >
      <div className="stack">
        <div className="form-grid">
          <div className="field">
            <label>Quantidade
            <input
              className="input"
              inputMode="numeric"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
            </label>
          </div>
          <div className="field">
            <label>Preço unitário
            <input className="input" value={money(product.price)} readOnly disabled />
            </label>
          </div>
        </div>

        <div className="field">
          <label>Forma de pagamento
          <select
            className="input"
            value={method}
            onChange={(e) => setMethod(e.target.value as CashPaymentMethod)}
          >
            {PAYMENT_METHODS.map((m) => (
              <option key={m.value} value={m.value}>{m.label}</option>
            ))}
          </select>
            </label>
        </div>

        <div className="field">
          <label>Barbeiro (opcional)
          <select
            className="input"
            value={professionalId}
            onChange={(e) => setProfessionalId(e.target.value)}
          >
            <option value="">Sem barbeiro — não gera comissão</option>
            {activeProfessionals.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
            </label>
          <span className="faint" style={{ fontSize: 12 }}>
            Com barbeiro escolhido, a venda entra na comissão dele no relatório do dia.
          </span>
        </div>

        <div className="row between" style={{ paddingTop: 4 }}>
          <span className="muted">Total da venda</span>
          <strong className="mono" style={{ fontSize: 18 }}>{money(total)}</strong>
        </div>

        <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
          A venda entra no <strong>caixa aberto</strong> como movimento de venda. Se o caixa
          estiver fechado, abra-o antes — senão o dinheiro não aparece na conferência da gaveta.
        </p>

        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
