// Caixa (V18): o ritual diário — abrir com fundo de troco, registrar vendas/sangrias/suprimentos
// e fechar conferindo a gaveta. A "gaveta esperada" só conta dinheiro; PIX/cartão entram nos
// totais mas não na conferência física.
import { useState } from "react";
import { Hint } from "../components/Hint";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { Modal } from "../components/Modal";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import { money } from "../lib/format";
import { METHOD_LABEL } from "../lib/paymentMethods";
import type { CashMovementType, CashPaymentMethod, CashSessionSummary } from "../api/types";

const TYPE_LABEL: Record<CashMovementType, string> = {
  VENDA: "Venda",
  SANGRIA: "Sangria",
  SUPRIMENTO: "Suprimento",
};

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

export function Caixa() {
  const { data, loading, error, reload } = useApi<CashSessionSummary | null>(
    () => api.get("/api/admin/cash-register"),
    [],
  );

  return (
    <Layout title="Caixa">
      {loading ? (
        <div className="card"><div className="card-pad stack" style={{ gap: 12 }}>
          {[0, 1, 2].map((i) => <Skeleton key={i} h={46} />)}
        </div></div>
      ) : error ? (
        <div className="card"><ErrorState message={error} onRetry={reload} /></div>
      ) : data ? (
        <OpenCash summary={data} onChanged={reload} />
      ) : (
        <ClosedCash onOpened={reload} />
      )}
    </Layout>
  );
}

/** Caixa fechado: formulário de abertura com o fundo de troco. */
function ClosedCash({ onOpened }: { onOpened: () => void }) {
  const toast = useToast();
  const [opening, setOpening] = useState("0");
  const [saving, setSaving] = useState(false);

  const open = async () => {
    setSaving(true);
    try {
      await api.post("/api/admin/cash-register/open", { openingAmount: Number(opening) || 0 });
      toast.success("Caixa aberto ✓");
      onOpened();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao abrir o caixa.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card" style={{ maxWidth: 460 }}>
      <div className="card-head"><h3>Abrir o caixa</h3><span className="badge badge-muted">fechado</span></div>
      <div className="card-pad">
        <EmptyState
          icon="🔒"
          title="O caixa está fechado."
          hint="Informe o fundo de troco para começar o dia. Todo movimento fica registrado até o fechamento."
        />
        <div className="field" style={{ marginTop: 8 }}>
          <label>Fundo de troco (R$)
          <input
            className="input"
            type="number"
            min="0"
            step="0.01"
            value={opening}
            onChange={(e) => setOpening(e.target.value)}
            style={{ maxWidth: 200 }}
          />
            </label>
        </div>
        <button className="btn btn-primary" onClick={open} disabled={saving} style={{ marginTop: 12 }}>
          {saving ? "Abrindo…" : "Abrir caixa"}
        </button>
      </div>
    </div>
  );
}

/** Caixa aberto: KPIs da gaveta, registro de movimentos, lista e fechamento. */
function OpenCash({ summary, onChanged }: { summary: CashSessionSummary; onChanged: () => void }) {
  const [showClose, setShowClose] = useState(false);
  const t = summary.totals;
  const cartao = t.vendasDebito + t.vendasCredito;

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div className="row between wrap" style={{ gap: 12 }}>
        <div className="muted" style={{ fontSize: 13.5 }}>
          Aberto por <strong>{summary.openedByName ?? "—"}</strong> às {timeOf(summary.openedAt)} · fundo{" "}
          <strong>{money(summary.openingAmount)}</strong>
        </div>
        <button className="btn btn-primary" onClick={() => setShowClose(true)}>Fechar caixa</button>
      </div>

      <div className="stat-grid">
        <div className="stat stat-hero">
          <div className="label">Esperado na gaveta</div>
          <div className="value">{money(summary.expectedDrawer)}</div>
          <div className="sub">fundo + suprimentos − sangrias + vendas em dinheiro</div>
        </div>
        <div className="stat">
          <div className="label">Vendas (total)</div>
          <div className="value">{money(t.vendasTotal)}</div>
          <div className="sub">{summary.movements.filter((m) => m.type === "VENDA").length} venda(s)</div>
        </div>
        {/* Vários valores num card só: em lista rótulo × valor. Enfileirados com "·" num número de
            18px eles quebravam no meio ("R$ 20,00 · R$ 65,00 · / R$ 172,00") e sem dizer qual era qual. */}
        <div className="stat stat-wide">
          <div className="label">Por forma de pagamento</div>
          <ul className="stat-split">
            <li><span>Dinheiro</span><b className="mono">{money(t.vendasDinheiro)}</b></li>
            <li><span>PIX</span><b className="mono">{money(t.vendasPix)}</b></li>
            <li><span>Cartão</span><b className="mono">{money(cartao)}</b></li>
            {t.vendasOutro > 0 && <li><span>Outro</span><b className="mono">{money(t.vendasOutro)}</b></li>}
          </ul>
          <div className="sub">
            só o dinheiro entra na gaveta{t.taxas > 0 ? ` · taxas ${money(t.taxas)}` : ""}
          </div>
        </div>
        <div className="stat stat-wide">
          <div className="label">Sangrias e suprimentos</div>
          <ul className="stat-split">
            <li>
              <span>Sangrias<Hint term="sangria" label="sangria" align="start" /></span>
              <b className="mono">− {money(t.sangrias)}</b>
            </li>
            <li>
              <span>Suprimentos<Hint term="suprimento" label="suprimento" align="start" /></span>
              <b className="mono">+ {money(t.suprimentos)}</b>
            </li>
          </ul>
        </div>
      </div>

      <MovementForm onAdded={onChanged} />

      <div className="card">
        <div className="card-head"><h3>Movimentos do dia</h3><span className="badge badge-muted">{summary.movements.length}</span></div>
        <div className="card-pad">
          {summary.movements.length === 0 ? (
            <EmptyState icon="🧾" title="Nenhum movimento ainda." hint="Registre a primeira venda, sangria ou suprimento acima." />
          ) : (
            <div className="stack" style={{ gap: 8 }}>
              {[...summary.movements].reverse().map((m) => (
                <div key={m.id} className="row between" style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 550, fontSize: 14 }}>
                      {TYPE_LABEL[m.type]}
                      {m.paymentMethod && <span className="muted" style={{ fontWeight: 400 }}> · {METHOD_LABEL[m.paymentMethod]}</span>}
                      {m.description && <span className="muted" style={{ fontWeight: 400 }}> · {m.description}</span>}
                      {m.feeAmount > 0 && <span className="faint" style={{ fontWeight: 400 }}> · taxa {money(m.feeAmount)}</span>}
                    </div>
                    <div className="mono" style={{ fontSize: 12, color: "var(--text-faint)" }}>{timeOf(m.createdAt)}</div>
                  </div>
                  <span className="mono" style={{ fontWeight: 600, whiteSpace: "nowrap", flexShrink: 0, color: m.type === "SANGRIA" ? "var(--red)" : "var(--text)" }}>
                    {m.type === "SANGRIA" ? "− " : "+ "}{money(m.amount)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {showClose && (
        <CloseCashModal summary={summary} onClose={() => setShowClose(false)} onClosed={onChanged} />
      )}
    </div>
  );
}

/** Formulário compacto para registrar um movimento no caixa aberto. */
function MovementForm({ onAdded }: { onAdded: () => void }) {
  const toast = useToast();
  const [type, setType] = useState<CashMovementType>("VENDA");
  const [method, setMethod] = useState<CashPaymentMethod>("DINHEIRO");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);

  const valid = Number(amount) > 0;

  const submit = async () => {
    setSaving(true);
    try {
      await api.post("/api/admin/cash-register/movements", {
        type,
        paymentMethod: type === "VENDA" ? method : null,
        amount: Number(amount),
        description: description.trim() || null,
      });
      toast.success(`${TYPE_LABEL[type]} registrada ✓`);
      setAmount("");
      setDescription("");
      onAdded();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao registrar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head"><h3>Registrar movimento</h3></div>
      <div className="card-pad">
        <div className="chip-row" style={{ marginBottom: 12 }}>
          {(["VENDA", "SANGRIA", "SUPRIMENTO"] as CashMovementType[]).map((tp) => (
            <button key={tp} type="button" className={`chip-btn ${type === tp ? "active" : ""}`} onClick={() => setType(tp)}>
              {TYPE_LABEL[tp]}
            </button>
          ))}
        </div>
        <div className="form-grid">
          {type === "VENDA" && (
            <div className="field">
              <label>Forma de pagamento
              <select className="input" value={method} onChange={(e) => setMethod(e.target.value as CashPaymentMethod)}>
                {(Object.keys(METHOD_LABEL) as CashPaymentMethod[]).map((mth) => (
                  <option key={mth} value={mth}>{METHOD_LABEL[mth]}</option>
                ))}
              </select>
            </label>
            </div>
          )}
          <div className="field">
            <label>Valor (R$)
            <input className="input" type="number" min="0" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </label>
          </div>
          <div className="field">
            <label>Descrição (opcional)
            <input className="input" value={description} placeholder="Ex.: corte do João, troco, depósito" onChange={(e) => setDescription(e.target.value)} />
            </label>
          </div>
        </div>
        <button className="btn btn-primary" onClick={submit} disabled={!valid || saving} style={{ marginTop: 12 }}>
          {saving ? "Registrando…" : "Registrar"}
        </button>
      </div>
    </div>
  );
}

/** Fechamento: informa o valor contado e mostra a conferência (esperado × contado). */
function CloseCashModal({ summary, onClose, onClosed }: { summary: CashSessionSummary; onClose: () => void; onClosed: () => void }) {
  const toast = useToast();
  const [counted, setCounted] = useState("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const countedNum = Number(counted);
  const diff = counted === "" ? null : countedNum - summary.expectedDrawer;

  const close = async () => {
    setSaving(true);
    try {
      const result = await api.post<CashSessionSummary>("/api/admin/cash-register/close", {
        counted: countedNum || 0,
        notes: notes.trim() || null,
      });
      const d = result.difference ?? 0;
      const label = d === 0 ? "sem diferença" : d > 0 ? `sobra de ${money(d)}` : `falta de ${money(Math.abs(d))}`;
      toast.success(`Caixa fechado ✓ ${label}`);
      onClosed();
      onClose();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao fechar o caixa.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="Fechar o caixa"
      onClose={onClose}
      footer={
        <>
          {/* Mesmo rodapé dos outros modais: sair sem fechar não pode depender só do "✕". */}
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button className="btn btn-primary" onClick={close} disabled={counted === "" || saving}>
            {saving ? "Fechando…" : "Confirmar fechamento"}
          </button>
        </>
      }
    >
      <div className="stack" style={{ gap: 14 }}>
        <div className="row between" style={{ padding: "10px 12px", background: "var(--surface)", borderRadius: "var(--radius-sm)" }}>
          <span className="muted">Esperado na gaveta</span>
          <span className="mono" style={{ fontWeight: 600 }}>{money(summary.expectedDrawer)}</span>
        </div>
        <div className="field">
          <label>Valor contado na gaveta (R$)
          <input className="input" type="number" min="0" step="0.01" value={counted} onChange={(e) => setCounted(e.target.value)} />
            </label>
        </div>
        {diff !== null && (
          <div className={`row between ${diff === 0 ? "" : ""}`} style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
            <span className="muted">Diferença</span>
            <span className="mono" style={{ fontWeight: 600, color: diff === 0 ? "var(--green)" : diff > 0 ? "var(--text)" : "var(--red)" }}>
              {diff > 0 ? "sobra " : diff < 0 ? "falta " : ""}{money(Math.abs(diff))}
            </span>
          </div>
        )}
        <div className="field">
          <label>Observações (opcional)
          <input className="input" value={notes} placeholder="Ex.: falta por troco a mais" onChange={(e) => setNotes(e.target.value)} />
            </label>
        </div>
      </div>
    </Modal>
  );
}
