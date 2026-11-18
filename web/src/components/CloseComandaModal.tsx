// Fechamento da comanda (V35): uma ou mais formas de pagamento. Cada linha vira o seu
// movimento no caixa — só a parte em dinheiro entra na gaveta. A soma tem que bater com o
// total; o botão "restante em…" fecha a conta sem ninguém fazer conta de cabeça.
//
// O total a cobrar vem do SERVIDOR (`close-preview`), com o pacote do cliente e o cupom já
// abatidos — nunca de conta feita aqui. Se o backend ainda não tiver esse endpoint (deploys
// fora de ordem), cai no comportamento antigo: total da comanda + `coupon-preview`.
import { useCallback, useEffect, useRef, useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { api, ApiError } from "../api/client";
import type { CashPaymentMethod, Comanda, ComandaClosePreview, ComandaCouponPreview } from "../api/types";
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

/** Endpoint inexistente neste backend → usar o fluxo antigo. */
function isMissingEndpoint(e: unknown): boolean {
  return e instanceof ApiError && (e.status === 404 || e.status === 405);
}

let nextKey = 1;

/** loading: esperando a prévia; server: prévia com pacote; fallback: backend sem `close-preview`. */
type Mode = "loading" | "server" | "fallback";

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
  const [mode, setMode] = useState<Mode>("loading");
  const [preview, setPreview] = useState<ComandaClosePreview | null>(null);
  /** Cupom aceito pelo servidor (em qualquer modo). */
  const [appliedCode, setAppliedCode] = useState<string | null>(null);
  /** Só no modo fallback: a prévia antiga de cupom. */
  const [legacy, setLegacy] = useState<ComandaCouponPreview | null>(null);
  const [checkingCoupon, setCheckingCoupon] = useState(false);
  const [previewWarning, setPreviewWarning] = useState<string | null>(null);
  const [lines, setLines] = useState<Line[]>([
    { key: nextKey++, method: "DINHEIRO", amount: formatAmount(comanda.currentTotal) },
  ]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Código do pedido de prévia mais recente (null = sem cupom). Resposta de um pedido que já
  // não é o atual (a pessoa trocou/removeu o cupom no meio) é descartada.
  const latestCode = useRef<string | null>(null);

  const serverPreview = mode === "server" ? preview : null;
  const subtotal = serverPreview ? serverPreview.subtotal : comanda.currentTotal;
  const total = serverPreview ? serverPreview.total : legacy ? legacy.finalTotal : comanda.currentTotal;
  const couponDiscount = serverPreview ? serverPreview.couponDiscount : (legacy?.discount ?? 0);
  const nothingToCharge = mode !== "loading" && cents(total) <= 0;

  const amounts = lines.map((l) => parseAmount(l.amount));
  const invalidLine = amounts.some((a) => !Number.isFinite(a) || a <= 0);
  const paid = cents(amounts.reduce((s, a) => s + (Number.isFinite(a) ? a : 0), 0));
  const remaining = nothingToCharge ? 0 : cents(total - paid);
  const balanced = mode !== "loading" && (nothingToCharge || (remaining === 0 && !invalidLine));
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
  const retotal = useCallback(
    (newTotal: number) =>
      setLines((ls) => (ls.length === 1 && newTotal > 0 ? [{ ...ls[0], amount: formatAmount(newTotal) }] : ls)),
    [],
  );

  /** Fluxo antigo de cupom (backend sem `close-preview`). */
  const legacyCoupon = useCallback(
    async (code: string) => {
      const p = await api.post<ComandaCouponPreview>(`/api/admin/comandas/${comanda.id}/coupon-preview`, {
        code,
      });
      if (latestCode.current !== code) return;
      setLegacy(p);
      setAppliedCode(p.code);
      retotal(p.finalTotal);
    },
    [comanda.id, retotal],
  );

  /** Pede ao servidor o total com o pacote do cliente (e o cupom, se houver). */
  const loadPreview = useCallback(
    async (code: string | null, fallback: boolean) => {
      latestCode.current = code;
      setCheckingCoupon(code !== null);
      setError(null);
      try {
        if (fallback) {
          if (code) await legacyCoupon(code);
          return;
        }
        const p = await api.post<ComandaClosePreview>(`/api/admin/comandas/${comanda.id}/close-preview`, {
          couponCode: code,
        });
        if (latestCode.current !== code) return;
        setMode("server");
        setPreview(p);
        setPreviewWarning(null);
        if (code && p.couponError) {
          setAppliedCode(null);
          setError(p.couponError);
        } else {
          setAppliedCode(code);
        }
        retotal(p.total);
      } catch (e) {
        if (latestCode.current !== code) return;
        if (!fallback && isMissingEndpoint(e)) {
          // Backend ainda sem a prévia de fechamento: segue no fluxo antigo.
          setMode("fallback");
          setPreview(null);
          if (code) {
            try {
              await legacyCoupon(code);
            } catch (e2) {
              if (latestCode.current === code) {
                setAppliedCode(null);
                setError(e2 instanceof ApiError ? e2.message : "Não foi possível validar o cupom.");
              }
            }
          }
          return;
        }
        if (code) {
          setAppliedCode(null);
          setError(e instanceof ApiError ? e.message : "Não foi possível validar o cupom.");
        } else {
          // Sem a prévia não dá para saber o que o pacote cobre: usa o total cheio e avisa.
          setMode("fallback");
          setPreview(null);
          setPreviewWarning("Não foi possível conferir o pacote do cliente — usando o total da comanda.");
        }
      } finally {
        if (latestCode.current === code) setCheckingCoupon(false);
      }
    },
    [comanda.id, legacyCoupon, retotal],
  );

  // Ao abrir: total com o pacote do cliente já abatido.
  useEffect(() => {
    void loadPreview(null, false);
  }, [loadPreview]);

  const applyCoupon = () => {
    const code = coupon.trim();
    // Enter repetido enquanto um cupom ainda está sendo conferido não dispara outro pedido.
    if (!code || checkingCoupon) return;
    void loadPreview(code, mode === "fallback");
  };

  const removeCoupon = () => {
    setCoupon("");
    setAppliedCode(null);
    setLegacy(null);
    if (mode === "fallback") {
      latestCode.current = null;
      setCheckingCoupon(false);
      retotal(comanda.currentTotal);
    } else {
      void loadPreview(null, false);
    }
  };

  const submit = async () => {
    if (!balanced) return;
    setSaving(true);
    setError(null);
    try {
      await api.post(`/api/admin/comandas/${comanda.id}/close`, {
        // Nada a cobrar (pacote/cupom cobriram tudo): nenhum movimento de pagamento.
        payments: nothingToCharge
          ? []
          : lines.map((l, i) => ({ method: l.method, amount: cents(amounts[i]) })),
        couponCode: appliedCode,
      });
      if (nothingToCharge) {
        toast.success("Comanda fechada ✓ Nada a cobrar — coberto pelo pacote/cupom.");
      } else {
        const resumo =
          lines.length > 1 ? ` (${lines.map((l) => METHOD_LABEL[l.method]).join(" + ")})` : "";
        toast.success(`Comanda fechada ✓ ${money(total)} recebido${resumo}.`);
      }
      onClosed();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao fechar a comanda.");
    } finally {
      setSaving(false);
    }
  };

  const discounted = mode !== "loading" && cents(subtotal - total) !== 0;

  return (
    <Modal
      title={`Fechar comanda${comanda.customerName ? ` — ${comanda.customerName}` : ""}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Voltar</button>
          <button className="btn btn-primary" onClick={submit} disabled={saving || !balanced}>
            {saving
              ? "Fechando…"
              : mode === "loading"
                ? "Calculando…"
                : nothingToCharge
                  ? "Fechar sem cobrança"
                  : `Fechar ${money(total)}`}
          </button>
        </>
      }
    >
      <div className="stack" style={{ gap: 14 }}>
        <div className="row between">
          <span className="muted">{mode === "loading" ? "Calculando o total…" : "Total a cobrar"}</span>
          <span className="mono">
            {discounted && (
              <span className="faint" style={{ textDecoration: "line-through", marginRight: 8 }}>
                {money(subtotal)}
              </span>
            )}
            <strong style={{ fontSize: 20 }}>{money(total)}</strong>
          </span>
        </div>

        {/* O que o pacote do cliente cobre — calculado no servidor. */}
        {serverPreview && serverPreview.coveredItems.length > 0 && (
          <ul className="stack" style={{ gap: 4, margin: 0, padding: 0, listStyle: "none" }}>
            {serverPreview.coveredItems.map((it) => (
              <li key={it.itemId} className="row between" style={{ fontSize: 13.5 }}>
                <span>
                  {it.name} <span className="badge badge-green">coberto pelo pacote</span>
                </span>
                <span className="faint">
                  {it.uses} {it.uses === 1 ? "uso" : "usos"}
                </span>
              </li>
            ))}
            {serverPreview.packageCovered > 0 && (
              <li className="row between faint" style={{ fontSize: 13 }}>
                <span>Abatido pelo pacote</span>
                <span className="mono">− {money(serverPreview.packageCovered)}</span>
              </li>
            )}
          </ul>
        )}

        {previewWarning && <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>{previewWarning}</p>}

        {/* Cupom: o total descontado vem do servidor, nunca de conta feita aqui. */}
        {appliedCode ? (
          <div className="row between">
            <span className="badge badge-green">
              Cupom {appliedCode} · − {money(couponDiscount)}
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
              onKeyDown={(e) => {
                if (e.key === "Enter") applyCoupon();
              }}
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

        {nothingToCharge ? (
          <div
            className="row between"
            role="status"
            style={{
              padding: "10px 12px",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border)",
            }}
          >
            <span className="muted">Nada a cobrar — coberto pelo pacote/cupom.</span>
            <strong className="mono" style={{ color: "var(--green)" }}>✓</strong>
          </div>
        ) : (
          <>
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
          </>
        )}

        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
