// Estoque de um produto (V24): repor, registrar perda, corrigir contagem e ver o extrato.
// O controle é opt-in — enquanto desligado, o saldo aqui não bloqueia nenhuma venda.
import { useState } from "react";
import { Modal, Switch } from "./Modal";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Product, StockMovement } from "../api/types";

const DATE_FMT = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

const MOVEMENT_LABEL: Record<StockMovement["type"], string> = {
  ENTRADA: "Reposição",
  SAIDA: "Saída",
  AJUSTE: "Ajuste",
};

type Action = "entrada" | "saida" | "ajuste";

export function StockModal({
  product,
  onClose,
  onChanged,
}: {
  product: Product;
  onClose: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const movements = useApi<StockMovement[]>(
    () => api.get(`/api/admin/stock/${product.id}/movements`),
    [version],
  );

  const [action, setAction] = useState<Action>("entrada");
  const [quantity, setQuantity] = useState("");
  const [note, setNote] = useState("");
  const [trackStock, setTrackStock] = useState(product.trackStock);
  const [minStock, setMinStock] = useState(String(product.minStock));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<unknown>, msg: string) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      toast.success(msg);
      setQuantity("");
      setNote("");
      setVersion((v) => v + 1);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro na operação.");
    } finally {
      setBusy(false);
    }
  };

  const submit = () => {
    const qty = Number(quantity);
    if (!Number.isInteger(qty) || qty < 0 || (action !== "ajuste" && qty === 0)) {
      setError(
        action === "ajuste"
          ? "Informe a quantidade contada (0 ou mais)."
          : "A quantidade deve ser um número inteiro maior que zero.",
      );
      return;
    }
    const body =
      action === "ajuste"
        ? { countedQuantity: qty, note: note.trim() || null }
        : { quantity: qty, note: note.trim() || null };
    const label =
      action === "entrada" ? "Reposição registrada ✓"
        : action === "saida" ? "Saída registrada ✓"
          : "Contagem ajustada ✓";
    run(() => api.post(`/api/admin/stock/${product.id}/${action}`, body), label);
  };

  const saveConfig = () => {
    const min = Number(minStock);
    if (!Number.isInteger(min) || min < 0) {
      setError("O estoque mínimo deve ser um número inteiro maior ou igual a zero.");
      return;
    }
    run(
      () => api.put(`/api/admin/stock/${product.id}/config`, { trackStock, minStock: min }),
      "Configuração de estoque salva ✓",
    );
  };

  return (
    <Modal title={`Estoque — ${product.name}`} onClose={onClose}>
      <div className="stack">
        {/* Saldo atual */}
        <div className="row between" style={{ paddingBottom: 4 }}>
          <span className="muted">Saldo atual</span>
          <strong className="mono" style={{ fontSize: 22 }}>
            {product.trackStock ? product.stockQuantity : "—"}
            {product.lowStock && (
              <span className="badge badge-amber" style={{ marginLeft: 10 }}>Repor</span>
            )}
          </strong>
        </div>

        {/* Configuração */}
        <div className="card-pad" style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
          <Switch checked={trackStock} onChange={setTrackStock} label="Controlar estoque deste produto" />
          <div className="field" style={{ marginTop: 10 }}>
            <label>Avisar quando restar (unidades)
            <input
              className="input"
              inputMode="numeric"
              value={minStock}
              onChange={(e) => setMinStock(e.target.value)}
            />
            </label>
            <span className="faint" style={{ fontSize: 12 }}>
              0 = sem alerta. Com o controle desligado, o saldo nunca bloqueia uma venda.
            </span>
          </div>
          <button className="btn btn-sm" onClick={saveConfig} disabled={busy}>
            Salvar configuração
          </button>
        </div>

        {/* Movimentar */}
        <div className="field">
          <label>Movimentar estoque</label>
          <div className="row wrap" style={{ gap: 8 }}>
            {(["entrada", "saida", "ajuste"] as const).map((a) => (
              <button
                key={a}
                className={`btn btn-sm ${action === a ? "btn-primary" : "btn-ghost"}`}
                onClick={() => setAction(a)}
              >
                {a === "entrada" ? "Repor" : a === "saida" ? "Perda" : "Contagem"}
              </button>
            ))}
          </div>
        </div>

        <div className="form-grid">
          <div className="field">
            <label>{action === "ajuste" ? "Quantidade contada" : "Quantidade"}
            <input
              className="input"
              inputMode="numeric"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
            </label>
          </div>
          <div className="field">
            <label>Observação (opcional)
            <input
              className="input"
              value={note}
              placeholder={action === "entrada" ? "ex.: Nota 123" : action === "saida" ? "ex.: quebrou" : "ex.: contagem do mês"}
              onChange={(e) => setNote(e.target.value)}
            />
            </label>
          </div>
        </div>

        <button className="btn btn-primary" onClick={submit} disabled={busy}>
          {busy ? "Registrando…" : "Registrar"}
        </button>

        {error && <div className="error">{error}</div>}

        {/* Extrato */}
        <div>
          <label className="faint" style={{ fontSize: 12 }}>EXTRATO</label>
          {movements.loading ? (
            <div className="muted">Carregando…</div>
          ) : movements.error ? (
            <div className="error">{movements.error}</div>
          ) : !movements.data || movements.data.length === 0 ? (
            <div className="faint" style={{ fontSize: 13 }}>
              Nenhum movimento ainda. Reposições, vendas e ajustes aparecem aqui.
            </div>
          ) : (
            <div className="stack" style={{ gap: 4, maxHeight: 220, overflowY: "auto" }}>
              {movements.data.map((m) => (
                <div key={m.id} className="row between" style={{ fontSize: 13, padding: "6px 0" }}>
                  <span className="mono faint" style={{ fontSize: 11.5, minWidth: 88 }}>
                    {DATE_FMT.format(new Date(m.createdAt))}
                  </span>
                  <span className="grow">
                    {MOVEMENT_LABEL[m.type]}
                    {m.note && <span className="faint"> · {m.note}</span>}
                  </span>
                  <span className="mono" style={{ color: m.type === "SAIDA" ? "var(--red)" : "var(--green)" }}>
                    {m.type === "SAIDA" ? "−" : "+"}{m.quantity}
                  </span>
                  <span className="mono faint" style={{ minWidth: 42, textAlign: "right" }}>
                    ={m.resultingStock}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
