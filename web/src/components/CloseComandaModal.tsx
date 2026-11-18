// Fechamento da comanda (V35): uma ou mais formas de pagamento. Cada linha vira o seu
// movimento no caixa — só a parte em dinheiro entra na gaveta. A soma tem que bater com o
// total (já com o cupom); o botão "restante em…" fecha a conta sem ninguém fazer conta de cabeça.
import { useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { api, ApiError } from "../api/client";
import type { CashPaymentMethod, Comanda, ComandaCouponPreview } from "../api/types";
import { money } from "../lib/format";
import { cents, METHOD_LABEL, PAYMENT_METHODS } from "../lib/paymentMethods";

interface Line {
  key: number;
  method: CashPaymentMethod;
  amount: string;
}

/** "12,50" ou "12.50" → 12.5; vazio ou inválido → NaN. */
function parseAmount(s: string): number {
  const t = s.trim().replace(/\s/g, "").replace(",", ".");
  return t === "" ? NaN : Number(t);
}

/** 12.5 → "12,50" — o formato que a pessoa digitaria. */
function formatAmount(n: number): string {
  return n.toFixed(2).replace(".", ",");
}

let nextKey = 1;

export function CloseComandaModal({
  comanda,
  onClose,
  onClosed,
}: {
  comanda: Comanda;
  onClose: () => void;
  onClosed: () => void;
}) {
  const toast = useToast();
  const [coupon, setCoupon] = useState("");
  const [applied, setApplied] = useState<ComandaCouponPreview | null>(null);
  const [checkingCoupon, setCheckingCoupon] = useState(false);
  const total = applied ? applied.finalTotal : comanda.currentTotal;
  const [lines, setLines] = useState<Line[]>([
    { key: nextKey++, method: "DINHEIRO", amount: formatAmount(comanda.currentTotal) },
  ]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const amounts = lines.map((l) => parseAmount(l.amount));
  const invalidLine = amounts.some((a) => !Number.isFinite(a) || a <= 0);
  const paid = cents(amounts.reduce((s, a) => s + (Number.isFinite(a) ? a : 0), 0));
  const remaining = cents(total - paid);
  const balanced = remaining === 0 && !invalidLine;
  const cashPart = cents(
    lines.reduce((s, l, i) => s + (l.method === "DINHEIRO" && Number.isFinite(amounts[i]) ? amounts[i] : 0), 0),
  );

  const update = (key: number, patch: Partial<Line>) =>
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  const remove = (key: number) => setLines((ls) => ls.filter((l) => l.key !== key));

  /** Joga o que falta numa forma: soma na linha dessa forma, se existir; senão abre uma. */
  const remainingIn = (method: CashPaymentMethod) => {
    if (remaining <= 0) return;
    setLines((ls) => {
      const idx = ls.findIndex((l) => l.method === method);
      if (idx >= 0) {
        const current = parseAmount(ls[idx].amount);
        const base = Number.isFinite(current) ? current : 0;
        return ls.map((l, i) => (i === idx ? { ...l, amount: formatAmount(cents(base + remaining)) } : l));
      }
      return [...ls, { key: nextKey++, method, amount: formatAmount(remaining) }];
    });
  };

  /** Com uma linha só, ela acompanha o total (o caso comum: pagou tudo de um jeito). */
  const retotal = (newTotal: number) =>
    setLines((ls) => (ls.length === 1 ? [{ ...ls[0], amount: formatAmount(newTotal) }] : ls));

  const applyCoupon = async () => {
    if (!coupon.trim()) return;
    setCheckingCoupon(true);
    setError(null);
    try {
      const preview = await api.post<ComandaCouponPreview>(
        `/api/admin/comandas/${comanda.id}/coupon-preview`,
        { code: coupon.trim() },
      );
      setApplied(preview);
      retotal(preview.finalTotal);
    } catch (e) {
      setApplied(null);
      setError(e instanceof ApiError ? e.message : "Não foi possível validar o cupom.");
    } finally {
      setCheckingCoupon(false);
    }
  };

  const removeCoupon = () => {
    setApplied(null);
    setCoupon("");
    retotal(comanda.currentTotal);
  };

  const submit = async () => {
    if (!balanced) return;
    setSaving(true);
    setError(null);
    try {
      await api.post(`/api/admin/comandas/${comanda.id}/close`, {
        payments: lines.map((l, i) => ({ method: l.method, amount: cents(amounts[i]) })),
        couponCode: applied?.code ?? null,
      });
      const resumo =
        lines.length > 1 ? ` (${lines.map((l) => METHOD_LABEL[l.method]).join(" + ")})` : "";
      toast.success(`Comanda fechada ✓ ${money(total)} no caixa${resumo}.`);
      onClosed();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao fechar a comanda.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={`Fechar comanda${comanda.customerName ? ` — ${comanda.customerName}` : ""}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Voltar</button>
          <button className="btn btn-primary" onClick={submit} disabled={saving || !balanced}>
            {saving ? "Fechando…" : `Fechar ${money(total)}`}
          </button>
        </>
      }
    >
      <div className="stack" style={{ gap: 14 }}>
        <div className="row between">
          <span className="muted">Total da conta</span>
          <span className="mono">
            {applied && (
              <span className="faint" style={{ textDecoration: "line-through", marginRight: 8 }}>
                {money(comanda.currentTotal)}
              </span>
            )}
            <strong style={{ fontSize: 20 }}>{money(total)}</strong>
          </span>
        </div>

        {/* Cupom: o total descontado vem do servidor, nunca de conta feita aqui. */}
        {applied ? (
          <div className="row between">
            <span className="badge badge-green">
              Cupom {applied.code} · − {money(applied.discount)}
            </span>
            <button className="btn btn-ghost btn-sm" onClick={removeCoupon} disabled={saving}>
              Remover cupom
            </button>
          </div>
        ) : (
          <div className="row" style={{ gap: 8 }}>
            <input
              className="input grow"
              aria-label="Cupom de desconto"
              placeholder="Cupom de desconto (opcional)"
              value={coupon}
              onChange={(e) => setCoupon(e.target.value.toUpperCase())}
              onKeyDown={(e) => e.key === "Enter" && applyCoupon()}
            />
            <button
              className="btn btn-ghost btn-sm"
              onClick={applyCoupon}
              disabled={checkingCoupon || !coupon.trim()}
            >
              {checkingCoupon ? "…" : "Aplicar"}
            </button>
          </div>
        )}

        <div>
          <div className="muted" style={{ fontSize: 13, marginBottom: 6 }}>Como foi pago</div>
          <div className="stack" style={{ gap: 8 }}>
            {lines.map((l, i) => {
              const bad = !Number.isFinite(amounts[i]) || amounts[i] <= 0;
              return (
                <div key={l.key} className="row" style={{ gap: 8 }}>
                  <select
                    className="input"
                    aria-label={`Forma de pagamento da parte ${i + 1}`}
                    style={{ maxWidth: 150 }}
                    value={l.method}
                    onChange={(e) => update(l.key, { method: e.target.value as CashPaymentMethod })}
                  >
                    {PAYMENT_METHODS.map((m) => (
                      <option key={m.value} value={m.value}>{m.label}</option>
                    ))}
                  </select>
                  <input
                    className="input grow mono"
                    aria-label={`Valor da parte ${i + 1}`}
                    aria-invalid={bad}
                    inputMode="decimal"
                    value={l.amount}
                    onChange={(e) => update(l.key, { amount: e.target.value })}
                    style={bad ? { borderColor: "var(--red)" } : undefined}
                  />
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => remove(l.key)}
                    disabled={lines.length === 1 || saving}
                    title="Remover esta forma"
                    aria-label={`Remover parte ${i + 1}`}
                  >
                    ✕
                  </button>
                </div>
              );
            })}
          </div>
          <button
            className="btn btn-ghost btn-sm"
            style={{ marginTop: 8 }}
            onClick={() =>
              setLines((ls) => [
                ...ls,
                { key: nextKey++, method: "PIX", amount: remaining > 0 ? formatAmount(remaining) : "" },
              ])
            }
            disabled={saving}
          >
            + Dividir em outra forma
          </button>
        </div>

        <div
          className="row between"
          role="status"
          style={{
            padding: "10px 12px",
            borderRadius: "var(--radius-sm)",
            border: "1px solid var(--border)",
          }}
        >
          <span className="muted">
            {remaining > 0 ? "Restante" : remaining < 0 ? "Passou do total" : "Conta fechada"}
          </span>
          <strong
            className="mono"
            style={{ color: remaining === 0 ? "var(--green)" : remaining < 0 ? "var(--red)" : undefined }}
          >
            {remaining === 0 ? "✓" : money(Math.abs(remaining))}
          </strong>
        </div>

        {remaining > 0 && (
          <div className="row wrap" style={{ gap: 6, alignItems: "center" }}>
            <span className="faint" style={{ fontSize: 13 }}>Restante em…</span>
            {PAYMENT_METHODS.map((m) => (
              <button
                key={m.value}
                className="btn btn-ghost btn-sm"
                onClick={() => remainingIn(m.value)}
                disabled={saving}
              >
                {m.label}
              </button>
            ))}
          </div>
        )}

        {lines.length > 1 && (
          <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
            Na gaveta entra só a parte em dinheiro: <b className="mono">{money(cashPart)}</b>.
          </p>
        )}

        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
