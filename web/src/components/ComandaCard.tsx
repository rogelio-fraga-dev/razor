// Uma comanda aberta: os itens, o total corrente e as ações. O preço nunca é digitado aqui —
// vem do catálogo e é congelado no momento em que o item entra na conta.
import { useState } from "react";
import { useToast } from "./Toast";
import { api, ApiError } from "../api/client";
import type { Comanda, Product, ServiceOffering } from "../api/types";
import { money } from "../lib/format";
import { CloseComandaModal } from "./CloseComandaModal";

const TIME_FMT = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

export function ComandaCard({
  comanda,
  services,
  products,
  onChanged,
}: {
  comanda: Comanda;
  services: ServiceOffering[];
  products: Product[];
  onChanged: () => void;
}) {
  const toast = useToast();
  const [pick, setPick] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [closing, setClosing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // O select junta serviços e produtos; o prefixo diz para qual rota o item vai.
  const options = [
    ...services.filter((s) => s.active).map((s) => ({ key: `SERVICO:${s.id}`, label: `${s.name} — ${money(s.price)}` })),
    ...products.filter((p) => p.active).map((p) => ({ key: `PRODUTO:${p.id}`, label: `${p.name} — ${money(p.price)}` })),
  ];

  const run = async (action: () => Promise<unknown>, successMsg?: string) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      if (successMsg) toast.success(successMsg);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro na operação.");
    } finally {
      setBusy(false);
    }
  };

  const addItem = () => {
    if (!pick) return;
    const [kind, id] = pick.split(":");
    const qty = Number(quantity);
    if (!Number.isInteger(qty) || qty <= 0) {
      setError("A quantidade deve ser um número inteiro maior que zero.");
      return;
    }
    const path = kind === "SERVICO" ? "services" : "products";
    run(async () => {
      await api.post(`/api/admin/comandas/${comanda.id}/${path}`, { referenceId: id, quantity: qty });
      setPick("");
      setQuantity("1");
    });
  };

  const removeItem = (itemId: string) =>
    run(() => api.del(`/api/admin/comandas/${comanda.id}/items/${itemId}`));

  const cancel = () => run(() => api.post(`/api/admin/comandas/${comanda.id}/cancel`), "Comanda cancelada.");

  return (
    <div className="card">
      <div className="card-head">
        <h3>{comanda.customerName || "Sem nome"}</h3>
        <div className="row" style={{ gap: 10 }}>
          {comanda.professionalName && (
            <span className="badge badge-muted">✂ {comanda.professionalName}</span>
          )}
          <span className="badge badge-plan mono">{TIME_FMT.format(new Date(comanda.openedAt))}</span>
        </div>
      </div>

      <div className="card-pad">
        {comanda.items.length === 0 ? (
          <p className="faint" style={{ marginTop: 0, fontSize: 13.5 }}>
            Conta vazia — adicione um serviço ou produto abaixo.
          </p>
        ) : (
          <div className="stack" style={{ gap: 6, marginBottom: 14 }}>
            {comanda.items.map((i) => (
              <div
                key={i.id}
                className="row between"
                style={{ padding: "8px 10px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}
              >
                <div className="grow">
                  <span style={{ fontWeight: 550 }}>{i.description}</span>
                  <span className="faint" style={{ fontSize: 12 }}>
                    {" "}· {i.kind === "SERVICO" ? "serviço" : "produto"}
                    {i.quantity > 1 ? ` · ${i.quantity}x ${money(i.unitPrice)}` : ""}
                  </span>
                </div>
                <span className="mono" style={{ fontWeight: 600 }}>{money(i.total)}</span>
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => removeItem(i.id)}
                  disabled={busy}
                  title="Remover item"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}

        {/* Adicionar item */}
        <div className="row wrap" style={{ gap: 8, marginBottom: 14 }}>
          {/* Rótulo obrigatório: eram os dois únicos controles do painel sem <label> nem
              aria-label — e cada comanda aberta renderiza o seu, então o problema se
              multiplicava por comanda na tela. */}
          <select
            className="input grow"
            aria-label="Adicionar serviço ou produto à comanda"
            style={{ minWidth: 200 }}
            value={pick}
            onChange={(e) => setPick(e.target.value)}
          >
            <option value="">Adicionar serviço ou produto…</option>
            {options.map((o) => (
              <option key={o.key} value={o.key}>{o.label}</option>
            ))}
          </select>
          <input
            className="input"
            style={{ maxWidth: 80 }}
            inputMode="numeric"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            aria-label="Quantidade"
          />
          <button className="btn btn-ghost btn-sm" onClick={addItem} disabled={busy || !pick}>
            Adicionar
          </button>
        </div>

        <div className="row between" style={{ marginBottom: 14 }}>
          <span className="muted">Total da conta</span>
          <strong className="mono" style={{ fontSize: 20 }}>{money(comanda.currentTotal)}</strong>
        </div>

        <div className="row wrap" style={{ gap: 8 }}>
          <button
            className="btn btn-primary"
            onClick={() => setClosing(true)}
            disabled={busy || comanda.items.length === 0}
            title={comanda.items.length === 0 ? "Adicione ao menos um item" : undefined}
          >
            {busy ? "…" : `Fechar ${money(comanda.currentTotal)}`}
          </button>
          <button className="btn btn-danger btn-sm" onClick={cancel} disabled={busy}>
            Cancelar comanda
          </button>
        </div>

        {error && <div className="error" style={{ marginTop: 10 }}>{error}</div>}
      </div>

      {closing && (
        <CloseComandaModal comanda={comanda} onClose={() => setClosing(false)} onClosed={onChanged} />
      )}
    </div>
  );
}
