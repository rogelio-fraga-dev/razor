// Acerto de comissão (V25): o que cada barbeiro apurou no período, o que já recebeu e o saldo.
// É a tela que o dono olha antes de pagar — sem a coluna "já pago", pagar a mesma quinzena
// duas vezes é fácil demais.
import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { Pagination } from "../components/Pagination";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Advance, CommissionPayment, CommissionSettlement, CommissionSettlementItem, PageMeta } from "../api/types";
import { dateBr, money, todayIso } from "../lib/format";

function firstDayOfMonth(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

interface PayDraft {
  item: CommissionSettlementItem;
  amount: string;
  fromCashRegister: boolean;
  notes: string;
}

export function CommissionSettlementPage() {
  const toast = useToast();
  const [from, setFrom] = useState(firstDayOfMonth());
  const [to, setTo] = useState(todayIso());
  const [version, setVersion] = useState(0);
  const [draft, setDraft] = useState<PayDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [advanceDraft, setAdvanceDraft] = useState<CommissionSettlementItem | null>(null);
  const [advanceAmount, setAdvanceAmount] = useState("");
  const [advanceFromCash, setAdvanceFromCash] = useState(false);
  const [advanceNotes, setAdvanceNotes] = useState("");

  const settlement = useApi<CommissionSettlement>(
    () => api.get(`/api/admin/commission-payments/settlement?from=${from}&to=${to}`),
    [from, to, version],
  );
  // Duas listas independentes na mesma tela, cada uma com sua própria página: navegar no
  // histórico de acertos não pode arrastar a lista de vales junto.
  const [historyPage, setHistoryPage] = useState(0);
  const [advancesPage, setAdvancesPage] = useState(0);
  const history = useApi<PageMeta<CommissionPayment>>(
    () => api.get(`/api/admin/commission-payments?page=${historyPage}`),
    [historyPage, version],
  );
  const advances = useApi<PageMeta<Advance>>(
    () => api.get(`/api/admin/advances?page=${advancesPage}`),
    [advancesPage, version],
  );

  const openPay = (item: CommissionSettlementItem) => {
    setFormError(null);
    setDraft({
      item,
      // Sugere o saldo, não o total apurado: pagar de novo o que já foi pago é o erro a evitar.
      amount: item.aReceber > 0 ? String(item.aReceber) : "",
      fromCashRegister: false,
      notes: "",
    });
  };

  const openAdvance = (item: CommissionSettlementItem) => {
    setFormError(null);
    setAdvanceAmount("");
    setAdvanceFromCash(false);
    setAdvanceNotes("");
    setAdvanceDraft(item);
  };

  const grantAdvance = async () => {
    if (!advanceDraft) return;
    const amount = Number(advanceAmount.replace(",", "."));
    if (!Number.isFinite(amount) || amount <= 0) {
      setFormError("O valor do vale deve ser maior que zero.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await api.post("/api/admin/advances", {
        professionalId: advanceDraft.professionalId,
        amount,
        date: null, // hoje — é a data que define em qual acerto o vale entra
        fromCashRegister: advanceFromCash,
        notes: advanceNotes.trim() || null,
      });
      toast.success(`Vale de ${money(amount)} para ${advanceDraft.professionalName} registrado ✓`);
      setAdvanceDraft(null);
      setVersion((v) => v + 1);
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao registrar o vale.");
    } finally {
      setSaving(false);
    }
  };

  const pay = async () => {
    if (!draft) return;
    const amount = Number(draft.amount.replace(",", "."));
    if (!Number.isFinite(amount) || amount <= 0) {
      setFormError("O valor do acerto deve ser maior que zero.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await api.post("/api/admin/commission-payments", {
        professionalId: draft.item.professionalId,
        periodStart: from,
        periodEnd: to,
        amount,
        fromCashRegister: draft.fromCashRegister,
        notes: draft.notes.trim() || null,
      });
      toast.success(`Acerto de ${money(amount)} com ${draft.item.professionalName} registrado ✓`);
      setDraft(null);
      setVersion((v) => v + 1);
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao registrar o acerto.");
    } finally {
      setSaving(false);
    }
  };

  const s = settlement.data;

  return (
    <Layout
      title="Acerto de comissões"
      actions={
        <div className="row" style={{ gap: 8 }}>
          <input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={{ width: 150, padding: "6px 10px" }} />
          <span className="faint">até</span>
          <input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} style={{ width: 150, padding: "6px 10px" }} />
        </div>
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Apuração do período</h3>
          {s && (
            <div className="row" style={{ gap: 10 }}>
              <span className="badge badge-muted mono">apurado {money(s.totalComissao)}</span>
              <span className="badge badge-green mono">pago {money(s.totalPago)}</span>
              {s.totalVales > 0 && (
                <span className="badge badge-plasma mono">vales {money(s.totalVales)}</span>
              )}
              <span className="badge badge-amber mono">a pagar {money(s.totalAReceber)}</span>
            </div>
          )}
        </div>

        {settlement.loading ? (
          <SkeletonRows rows={3} />
        ) : settlement.error ? (
          <div className="empty error" role="alert">{settlement.error}</div>
        ) : !s || s.items.length === 0 ? (
          <EmptyState
            icon="✂"
            title="Nenhuma comissão apurada no período."
            hint="Comissões aparecem aqui quando há atendimentos com barbeiro atribuído ou vendas de produto no intervalo."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th style={{ textAlign: "right" }}>Serviços</th>
                  <th style={{ textAlign: "right" }}>Produtos</th>
                  <th style={{ textAlign: "right" }}>Comissão</th>
                  <th style={{ textAlign: "right" }}>Já pago</th>
                  <th style={{ textAlign: "right" }}>Vales</th>
                  <th style={{ textAlign: "right" }}>A pagar</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {s.items.map((it) => (
                  <tr key={it.professionalId}>
                    <td style={{ fontWeight: 550 }}>
                      {it.professionalName}
                      <div className="faint" style={{ fontSize: 11.5 }}>{it.atendimentos} atendimento(s)</div>
                    </td>
                    <td className="num">{money(it.faturamento)}</td>
                    <td className="num">
                      {it.produtos > 0 ? money(it.faturamentoProdutos) : <span className="faint">—</span>}
                    </td>
                    <td className="num">{money(it.comissao)}</td>
                    <td className="num" style={{ color: it.pago > 0 ? "var(--green)" : undefined }}>
                      {money(it.pago)}
                    </td>
                    <td className="num">
                      {it.vales > 0 ? (
                        <span style={{ color: "var(--amber)" }}>−{money(it.vales)}</span>
                      ) : (
                        <span className="faint">—</span>
                      )}
                    </td>
                    <td className="num" style={{ fontWeight: 700 }}>
                      {it.aReceber > 0 ? (
                        money(it.aReceber)
                      ) : it.aReceber < 0 ? (
                        <span style={{ color: "var(--red)" }} title="O vale passou da comissão: o barbeiro deve à barbearia">
                          {money(it.aReceber)}
                        </span>
                      ) : (
                        <span className="faint">quitado</span>
                      )}
                    </td>
                    <td className="right">
                      <button className="btn btn-ghost btn-sm" onClick={() => openAdvance(it)}>
                        Vale
                      </button>
                      <button className="btn btn-primary btn-sm" onClick={() => openPay(it)}>
                        Pagar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Histórico de acertos */}
      <div className="card">
        <div className="card-head">
          <h3>Acertos registrados</h3>
        </div>
        {history.loading && !history.data ? (
          <SkeletonRows rows={2} />
        ) : history.error ? (
          <div className="empty error" role="alert">{history.error}</div>
        ) : !history.data || history.data.items.length === 0 ? (
          <div className="card-pad faint">Nenhum acerto registrado ainda.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th>Período</th>
                  <th>Saída</th>
                  <th>Observação</th>
                  <th>Registrado por</th>
                  <th style={{ textAlign: "right" }}>Valor</th>
                </tr>
              </thead>
              <tbody>
                {history.data.items.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 550 }}>{p.professionalName ?? "—"}</td>
                    <td className="muted">{dateBr(p.periodStart)} – {dateBr(p.periodEnd)}</td>
                    <td>
                      {p.fromCashRegister ? (
                        <span className="badge badge-muted">Caixa</span>
                      ) : (
                        <span className="faint">Por fora</span>
                      )}
                    </td>
                    <td className="muted">{p.notes ?? "—"}</td>
                    <td className="faint">{p.paidByName ?? "—"}</td>
                    <td className="num">{money(p.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {history.data && (
          <Pagination page={history.data} onChange={setHistoryPage} label="acertos" busy={history.loading} />
        )}
      </div>

      {/* Vales do período — o que está sendo descontado acima */}
      <div className="card">
        <div className="card-head">
          <h3>Vales e adiantamentos</h3>
        </div>
        {advances.loading && !advances.data ? (
          <SkeletonRows rows={2} />
        ) : advances.error ? (
          <div className="empty error" role="alert">{advances.error}</div>
        ) : !advances.data || advances.data.items.length === 0 ? (
          <div className="card-pad faint">
            Nenhum vale registrado. Use o botão “Vale” na linha do barbeiro — o valor é
            descontado do acerto do período em que a data do vale cai.
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Barbeiro</th>
                  <th>Data</th>
                  <th>Saída</th>
                  <th>Observação</th>
                  <th>Liberado por</th>
                  <th style={{ textAlign: "right" }}>Valor</th>
                </tr>
              </thead>
              <tbody>
                {advances.data.items.map((a) => (
                  <tr key={a.id}>
                    <td style={{ fontWeight: 550 }}>{a.professionalName ?? "—"}</td>
                    <td className="muted">{dateBr(a.advanceDate)}</td>
                    <td>
                      {a.fromCashRegister ? (
                        <span className="badge badge-muted">Caixa</span>
                      ) : (
                        <span className="faint">Por fora</span>
                      )}
                    </td>
                    <td className="muted">{a.notes ?? "—"}</td>
                    <td className="faint">{a.grantedByName ?? "—"}</td>
                    <td className="num" style={{ color: "var(--amber)" }}>{money(a.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {advances.data && (
          <Pagination page={advances.data} onChange={setAdvancesPage} label="vales" busy={advances.loading} />
        )}
      </div>

      {advanceDraft && (
        <Modal
          title={`Vale para ${advanceDraft.professionalName}`}
          onClose={() => setAdvanceDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setAdvanceDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={grantAdvance} disabled={saving}>
                {saving ? "Registrando…" : "Registrar vale"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="row between">
              <span className="muted">Comissão apurada no período</span>
              <span className="mono">{money(advanceDraft.comissao)}</span>
            </div>
            {advanceDraft.vales > 0 && (
              <div className="row between">
                <span className="muted">Vales já dados no período</span>
                <span className="mono" style={{ color: "var(--amber)" }}>{money(advanceDraft.vales)}</span>
              </div>
            )}

            <div className="field">
              <label>Valor do vale (R$)
              <input
                className="input"
                inputMode="decimal"
                value={advanceAmount}
                placeholder="ex.: 100"
                onChange={(e) => setAdvanceAmount(e.target.value)}
              />
            </label>
              <span className="faint" style={{ fontSize: 12 }}>
                O vale entra com a data de hoje e é descontado do acerto do período que a contém.
              </span>
            </div>

            <Switch
              checked={advanceFromCash}
              onChange={setAdvanceFromCash}
              label="Tirar o dinheiro do caixa"
            />
            <span className="faint" style={{ fontSize: 12.5 }}>
              Ligado, lança uma <strong>sangria</strong> no caixa aberto (e exige caixa aberto).
              Desligado, é vale por fora — PIX, dinheiro do bolso — e não mexe na conferência
              da gaveta.
            </span>

            <div className="field">
              <label>Observação (opcional)
              <input
                className="input"
                value={advanceNotes}
                placeholder="ex.: pediu adiantado"
                onChange={(e) => setAdvanceNotes(e.target.value)}
              />
            </label>
            </div>

            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}

      {draft && (
        <Modal
          title={`Pagar ${draft.item.professionalName}`}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={pay} disabled={saving}>
                {saving ? "Registrando…" : "Registrar acerto"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="row between">
              <span className="muted">Período</span>
              <strong>{dateBr(from)} – {dateBr(to)}</strong>
            </div>
            <div className="row between">
              <span className="muted">Comissão apurada</span>
              <span className="mono">{money(draft.item.comissao)}</span>
            </div>
            {draft.item.pago > 0 && (
              <div className="row between">
                <span className="muted">Já pago neste período</span>
                <span className="mono" style={{ color: "var(--green)" }}>{money(draft.item.pago)}</span>
              </div>
            )}
            {draft.item.vales > 0 && (
              <div className="row between">
                <span className="muted">Vales descontados</span>
                <span className="mono" style={{ color: "var(--amber)" }}>−{money(draft.item.vales)}</span>
              </div>
            )}

            <div className="field">
              <label>Valor do acerto (R$)
              <input
                className="input"
                inputMode="decimal"
                value={draft.amount}
                onChange={(e) => setDraft({ ...draft, amount: e.target.value })}
              />
            </label>
              <span className="faint" style={{ fontSize: 12 }}>
                Sugerido: o saldo a pagar. Dá para registrar um valor menor (acerto parcial).
              </span>
            </div>

            <Switch
              checked={draft.fromCashRegister}
              onChange={(v) => setDraft({ ...draft, fromCashRegister: v })}
              label="Pagar com dinheiro do caixa"
            />
            <span className="faint" style={{ fontSize: 12.5 }}>
              Ligado, lança uma <strong>sangria</strong> no caixa aberto (e exige caixa aberto).
              Desligado, é pagamento por fora — PIX, transferência — e não mexe na conferência
              da gaveta.
            </span>

            <div className="field">
              <label>Observação (opcional)
              <input
                className="input"
                value={draft.notes}
                placeholder="ex.: PIX 15/07"
                onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
              />
            </label>
            </div>

            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
