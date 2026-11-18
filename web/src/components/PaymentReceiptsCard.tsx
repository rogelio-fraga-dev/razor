// Recebimentos por forma de pagamento (V35) e as taxas da maquininha. Lê os movimentos do
// caixa, então comanda paga em split aparece dividida certinho: R$ 20 no dinheiro e R$ 60 no
// crédito contam cada um na sua linha. As taxas configuradas aqui valem para as próximas
// vendas — o que já entrou guarda a taxa do dia em que entrou.
import { useState } from "react";
import { ErrorState } from "./ErrorState";
import { Modal } from "./Modal";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { CashPaymentMethod, FinancialSummary } from "../api/types";
import { money, percent } from "../lib/format";
import { FEE_METHODS, METHOD_LABEL } from "../lib/paymentMethods";

type Rates = Partial<Record<CashPaymentMethod, number>>;

export function PaymentReceiptsCard({ from, to, label }: { from: string; to: string; label: string }) {
  const [version, setVersion] = useState(0);
  const [editing, setEditing] = useState(false);
  const summary = useApi<FinancialSummary>(
    () => api.get(`/api/admin/reports/financial?from=${from}&to=${to}`),
    [from, to, version],
  );
  const rates = useApi<Rates>(() => api.get("/api/admin/payment-fees"), [version]);
  const r = summary.data?.receipts;
  const configured = FEE_METHODS.filter((m) => (rates.data?.[m] ?? 0) > 0);

  return (
    <div className="card">
      <div className="card-head">
        <h3>Recebimentos de {label}</h3>
        <button className="btn btn-ghost btn-sm" onClick={() => setEditing(true)}>
          Taxas da maquininha
        </button>
      </div>
      <div className="card-pad">
        {summary.loading ? (
          <SkeletonRows rows={3} />
        ) : summary.error ? (
          <ErrorState message={summary.error} onRetry={summary.reload} />
        ) : !r || r.methods.length === 0 ? (
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            Nenhuma venda passou pelo caixa neste período.
          </p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Forma</th>
                  <th style={{ textAlign: "right" }}>Pagamentos</th>
                  <th style={{ textAlign: "right" }}>Bruto</th>
                  <th style={{ textAlign: "right" }}>Taxas</th>
                  <th style={{ textAlign: "right" }}>Líquido</th>
                </tr>
              </thead>
              <tbody>
                {r.methods.map((l) => (
                  <tr key={l.method}>
                    <td style={{ fontWeight: 550 }}>{METHOD_LABEL[l.method]}</td>
                    <td className="num muted">{l.count}</td>
                    <td className="num">{money(l.gross)}</td>
                    <td className="num muted">{l.fees > 0 ? `− ${money(l.fees)}` : "—"}</td>
                    <td className="num">{money(l.net)}</td>
                  </tr>
                ))}
                <tr>
                  <td style={{ fontWeight: 700 }}>Total</td>
                  <td />
                  <td className="num" style={{ fontWeight: 700 }}>{money(r.gross)}</td>
                  <td className="num" style={{ fontWeight: 700 }}>{r.fees > 0 ? `− ${money(r.fees)}` : "—"}</td>
                  <td className="num" style={{ fontWeight: 700 }}>{money(r.net)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
        <p className="faint" style={{ fontSize: 12.5, margin: "12px 0 0" }}>
          O que entrou no caixa, por forma — comanda dividida conta em cada forma usada.
          {configured.length > 0
            ? ` Taxas: ${configured.map((m) => `${METHOD_LABEL[m]} ${percent(rates.data?.[m] ?? 0)}`).join(" · ")}.`
            : " Nenhuma taxa configurada."}
        </p>
      </div>

      {editing && (
        <FeesModal
          current={rates.data ?? {}}
          onClose={() => setEditing(false)}
          onSaved={() => setVersion((v) => v + 1)}
        />
      )}
    </div>
  );
}

function FeesModal({ current, onClose, onSaved }: { current: Rates; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [draft, setDraft] = useState<Record<string, string>>(() =>
    Object.fromEntries(FEE_METHODS.map((m) => [m, String(current[m] ?? 0).replace(".", ",")])),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    const body: Rates = {};
    for (const m of FEE_METHODS) {
      const n = Number((draft[m] ?? "").trim().replace(",", ".") || "0");
      if (!Number.isFinite(n) || n < 0 || n >= 100) {
        setError(`Taxa inválida em ${METHOD_LABEL[m]}: use um número entre 0 e 99,99.`);
        return;
      }
      body[m] = n;
    }
    setSaving(true);
    setError(null);
    try {
      await api.put("/api/admin/payment-fees", body);
      toast.success("Taxas salvas ✓ Valem para as próximas vendas.");
      onSaved();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao salvar as taxas.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Taxas por forma de pagamento"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Cancelar</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? "Salvando…" : "Salvar"}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginTop: 0, fontSize: 13.5 }}>
        Quanto a maquininha (ou o banco) fica de cada venda, em %. Entra como dedução no
        resultado do mês. Vendas já registradas não mudam — guardam a taxa do dia em que entraram.
      </p>
      <div className="stack" style={{ gap: 10 }}>
        {FEE_METHODS.map((m) => (
          <div key={m} className="field">
            <label>
              {METHOD_LABEL[m]} (%)
              <input
                className="input mono"
                inputMode="decimal"
                value={draft[m]}
                onChange={(e) => setDraft((d) => ({ ...d, [m]: e.target.value }))}
              />
            </label>
          </div>
        ))}
      </div>
      {error && <div className="error" role="alert" style={{ marginTop: 10 }}>{error}</div>}
    </Modal>
  );
}
