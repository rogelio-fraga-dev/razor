// Contas a pagar e resultado do mês (V31) — o outro lado do caixa.
//
// DRE e despesas moram na mesma tela de propósito: o número que o dono quer ("quanto sobrou?")
// só existe por causa das contas logo abaixo, e separar em duas telas faria ele conferir uma
// coisa num lugar e explicar noutro.
import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal, Switch } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import { Pagination } from "../components/Pagination";
import type { Dre, Expense, ExpenseCategory, ExpenseCategoryOption, PageMeta } from "../api/types";
import { dateBr, money, percent, todayIso } from "../lib/format";

interface Draft {
  id: string | null;
  description: string;
  category: ExpenseCategory;
  amount: string;
  dueDate: string;
  recurring: boolean;
  notes: string;
  /** Só no cadastro: lançar a conta já quitada (quem cadastra o boleto depois de pagar). */
  alreadyPaid: boolean;
  fromCashRegister: boolean;
}

function emptyDraft(): Draft {
  return {
    id: null,
    description: "",
    category: "ALUGUEL",
    amount: "",
    dueDate: todayIso(),
    recurring: false,
    notes: "",
    alreadyPaid: false,
    fromCashRegister: false,
  };
}

/** Mês corrente como "julho de 2026", para rotular o período do DRE sem inventar formato. */
const MONTH_FMT = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" });

export function Expenses() {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [paying, setPaying] = useState<Expense | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [page, setPage] = useState(0);

  const dre = useApi<Dre>(() => api.get("/api/admin/reports/dre"), [version]);
  const list = useApi<PageMeta<Expense>>(() => api.get(`/api/admin/expenses?page=${page}`), [page, version]);
  const categories = useApi<ExpenseCategoryOption[]>(() => api.get("/api/admin/expenses/categories"), []);

  const refresh = () => setVersion((v) => v + 1);
  const itens = list.data?.items ?? [];

  const openNew = () => {
    setFormError(null);
    setDraft(emptyDraft());
  };

  const openEdit = (e: Expense) => {
    setFormError(null);
    setDraft({
      id: e.id,
      description: e.description,
      category: e.category,
      amount: String(e.amount),
      dueDate: e.dueDate,
      recurring: e.recurring,
      notes: e.notes ?? "",
      alreadyPaid: false,
      fromCashRegister: false,
    });
  };

  const save = async () => {
    if (!draft) return;
    const amount = Number(draft.amount.replace(",", "."));
    if (!Number.isFinite(amount) || amount <= 0) {
      setFormError("O valor da despesa deve ser maior que zero.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (draft.id) {
        await api.put(`/api/admin/expenses/${draft.id}`, {
          description: draft.description.trim(),
          category: draft.category,
          amount,
          dueDate: draft.dueDate,
          recurring: draft.recurring,
          notes: draft.notes.trim() || null,
        });
        toast.success("Conta atualizada ✓");
      } else {
        await api.post("/api/admin/expenses", {
          description: draft.description.trim(),
          category: draft.category,
          amount,
          dueDate: draft.dueDate,
          paidAt: draft.alreadyPaid ? draft.dueDate : null,
          fromCashRegister: draft.alreadyPaid && draft.fromCashRegister,
          recurring: draft.recurring,
          notes: draft.notes.trim() || null,
        });
        toast.success("Conta lançada ✓");
      }
      setDraft(null);
      refresh();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  const confirmPay = async (fromCashRegister: boolean) => {
    if (!paying) return;
    setSaving(true);
    try {
      await api.post(`/api/admin/expenses/${paying.id}/pay`, {
        paidAt: todayIso(),
        fromCashRegister,
      });
      toast.success(`"${paying.description}" quitada ✓`);
      setPaying(null);
      refresh();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Não foi possível quitar.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (e: Expense) => {
    try {
      await api.del(`/api/admin/expenses/${e.id}`);
      toast.success("Conta apagada ✓");
      refresh();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Não foi possível apagar.");
    }
  };

  const repeat = async () => {
    try {
      const created = await api.post<Expense[]>("/api/admin/expenses/repeat");
      // Zero criadas é resultado legítimo (já estavam lá), não erro — dizer isso evita o
      // dono clicar de novo achando que não funcionou.
      toast.success(
        created.length === 0
          ? "As recorrentes do mês que vem já estavam lançadas."
          : `${created.length} conta(s) lançada(s) para o mês seguinte ✓`,
      );
      refresh();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Não foi possível repetir.");
    }
  };

  const periodLabel = dre.data ? MONTH_FMT.format(new Date(`${dre.data.from}T12:00:00`)) : "";
  const inTheRed = (dre.data?.result ?? 0) < 0;

  return (
    <Layout
      title="Despesas e resultado"
      actions={
        <>
          <button className="btn btn-ghost btn-sm" onClick={repeat}>
            Repetir recorrentes
          </button>
          <button className="btn btn-primary btn-sm" onClick={openNew}>
            + Nova conta
          </button>
        </>
      }
    >
      {/* ---------- DRE ---------- */}
      <div className="card">
        <div className="card-head">
          <h3>Resultado de {periodLabel}</h3>
          {dre.data && dre.data.marginPercent !== null && (
            <span className={`badge ${inTheRed ? "badge-amber" : "badge-green"} mono`}>
              margem {percent(dre.data.marginPercent)}
            </span>
          )}
        </div>

        {dre.loading ? (
          <SkeletonRows rows={3} />
        ) : dre.error ? (
          <div className="empty error">{dre.error}</div>
        ) : dre.data ? (
          <>
            <div className="bento-grid" style={{ marginBottom: 18 }}>
              <div className="card kpi-card card-edge">
                <div className="kpi-header"><span className="kpi-title">Entrou</span></div>
                <div className="kpi-value mono">{money(dre.data.totalRevenue)}</div>
                <div className="kpi-sub">
                  Serviços {money(dre.data.servicesRevenue)} · produtos {money(dre.data.productsRevenue)}
                </div>
              </div>
              <div className="card kpi-card">
                <div className="kpi-header"><span className="kpi-title">Saiu</span></div>
                <div className="kpi-value mono">{money(dre.data.totalExpenses)}</div>
                <div className="kpi-sub">
                  {money(dre.data.expensesPaid)} pago · {money(dre.data.expensesUnpaid)} em aberto
                </div>
              </div>
              <div className="card kpi-card">
                <div className="kpi-header"><span className="kpi-title">Sobrou</span></div>
                <div className="kpi-value mono" style={inTheRed ? { color: "var(--red)" } : undefined}>
                  {money(dre.data.result)}
                </div>
                <div className="kpi-sub">
                  {dre.data.marginPercent === null
                    ? "Sem faturamento no período"
                    : `${percent(dre.data.marginPercent)} do que entrou`}
                </div>
              </div>
            </div>

            {dre.data.overdueAmount > 0 && (
              <p style={{ margin: "0 0 14px" }}>
                <span className="badge badge-amber">
                  {money(dre.data.overdueAmount)} vencido e ainda em aberto
                </span>
              </p>
            )}

            {dre.data.expenses.length === 0 ? (
              <p className="muted" style={{ margin: 0, fontSize: 14 }}>
                Nenhuma despesa lançada neste período. Enquanto não houver, "sobrou" é só o
                faturamento — não o seu lucro.
              </p>
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Para onde foi</th>
                      <th style={{ textAlign: "right" }}>Valor</th>
                      <th style={{ textAlign: "right" }}>% do faturamento</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dre.data.expenses.map((l) => (
                      <tr key={l.category}>
                        <td style={{ fontWeight: 550 }}>{l.label}</td>
                        <td className="num">{money(l.amount)}</td>
                        <td className="num muted">
                          {l.shareOfRevenue === null ? "—" : percent(l.shareOfRevenue)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="faint" style={{ fontSize: 12.5, margin: "12px 0 0" }}>
              A despesa entra no mês do <b>vencimento</b>, não no dia em que foi paga — atrasar um
              boleto não faz o mês parecer mais lucrativo.
            </p>
          </>
        ) : null}
      </div>

      {/* ---------- Contas ---------- */}
      <div className="card">
        <div className="card-head">
          <h3>Contas</h3>
          {list.data && list.data.total > 0 && (
            // O DRE logo acima já dá "quanto está em aberto" em dinheiro, contado sobre a base
            // inteira. Aqui basta o total de contas — contar as pendentes desta página daria um
            // número menor que o real assim que houvesse mais de uma página.
            <span className="badge badge-muted">{list.data.total} conta(s)</span>
          )}
        </div>
        {list.loading && !list.data ? (
          <SkeletonRows rows={4} />
        ) : list.error ? (
          <div className="empty error">{list.error}</div>
        ) : itens.length === 0 ? (
          <EmptyState
            icon="📄"
            title="Nenhuma conta lançada."
            hint="Cadastre aluguel, energia, produtos e impostos. Sem isso o painel mostra só o que entra, e faturamento não é lucro."
            action={<button className="btn btn-primary" onClick={openNew}>+ Lançar conta</button>}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Descrição</th>
                  <th>Categoria</th>
                  <th>Vencimento</th>
                  <th style={{ textAlign: "right" }}>Valor</th>
                  <th>Situação</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {itens.map((e) => (
                  <tr key={e.id}>
                    <td style={{ fontWeight: 550 }}>
                      {e.description}
                      {e.recurring && (
                        <span className="badge badge-muted" style={{ marginLeft: 8 }}>mensal</span>
                      )}
                    </td>
                    <td className="muted">{e.categoryLabel}</td>
                    <td className="mono">{dateBr(e.dueDate)}</td>
                    <td className="num">{money(e.amount)}</td>
                    <td>
                      {e.paid ? (
                        <span className="badge badge-green">
                          Paga {e.paidAt ? `em ${dateBr(e.paidAt)}` : ""}
                        </span>
                      ) : e.overdue ? (
                        <span className="badge badge-amber">Vencida</span>
                      ) : (
                        <span className="badge badge-muted">Em aberto</span>
                      )}
                    </td>
                    <td className="right">
                      {!e.paid && (
                        <button className="btn btn-primary btn-sm" onClick={() => setPaying(e)}>
                          Quitar
                        </button>
                      )}
                      {/* Conta paga pelo caixa virou sangria: mexer nela faria a gaveta
                          discordar do relatório, então nem oferecemos a ação. */}
                      {!e.fromCashRegister && (
                        <>
                          <button className="btn btn-ghost btn-sm" onClick={() => openEdit(e)}>Editar</button>
                          <button className="btn btn-ghost btn-sm" onClick={() => remove(e)}>Apagar</button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {list.data && (
          <Pagination page={list.data} onChange={setPage} label="contas" busy={list.loading} />
        )}
      </div>

      {/* ---------- Quitação ---------- */}
      {paying && (
        <Modal
          title={`Quitar "${paying.description}"`}
          onClose={() => setPaying(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setPaying(null)} disabled={saving}>
                Cancelar
              </button>
              <button className="btn btn-ghost" onClick={() => confirmPay(false)} disabled={saving}>
                Paguei por fora
              </button>
              <button className="btn btn-primary" onClick={() => confirmPay(true)} disabled={saving}>
                {saving ? "Quitando…" : "Paguei em dinheiro"}
              </button>
            </>
          }
        >
          <p style={{ margin: "0 0 10px" }}>
            <strong>{money(paying.amount)}</strong> · vencimento {dateBr(paying.dueDate)}
          </p>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            <b>Paguei em dinheiro</b> lança uma sangria no caixa aberto — use quando o dinheiro
            saiu da gaveta. <b>Paguei por fora</b> (PIX, boleto, cartão da empresa) registra a
            quitação sem mexer na conferência do caixa.
          </p>
        </Modal>
      )}

      {/* ---------- Cadastro / edição ---------- */}
      {draft && (
        <Modal
          title={draft.id ? "Editar conta" : "Nova conta"}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>
                Cancelar
              </button>
              <button
                className="btn btn-primary"
                onClick={save}
                disabled={saving || !draft.description.trim()}
              >
                {saving ? "Salvando…" : "Salvar"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Descrição
                <input
                  className="input"
                  value={draft.description}
                  placeholder="ex.: Aluguel da loja"
                  onChange={(ev) => setDraft({ ...draft, description: ev.target.value })}
                />
              </label>
            </div>
            <div className="field">
              <label>Categoria
                <select
                  className="input"
                  value={draft.category}
                  onChange={(ev) => setDraft({ ...draft, category: ev.target.value as ExpenseCategory })}
                >
                  {(categories.data ?? []).map((c) => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </select>
              </label>
            </div>
            {categories.error && (
              <div className="error">
                Não foi possível carregar as categorias. Salvar agora pode gravar a categoria errada.
              </div>
            )}
            <div className="field">
              <label>Valor (R$)
                <input
                  className="input"
                  inputMode="decimal"
                  value={draft.amount}
                  placeholder="ex.: 2500,00"
                  onChange={(ev) => setDraft({ ...draft, amount: ev.target.value })}
                />
              </label>
            </div>
            <div className="field">
              <label>Vencimento
                <input
                  className="input"
                  type="date"
                  value={draft.dueDate}
                  onChange={(ev) => setDraft({ ...draft, dueDate: ev.target.value })}
                />
              </label>
            </div>
            <Switch
              checked={draft.recurring}
              onChange={(v) => setDraft({ ...draft, recurring: v })}
              label="Repete todo mês"
            />
            {draft.recurring && (
              <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
                Marcar aqui não cria as parcelas futuras sozinho. Quando virar o mês, use
                “Repetir recorrentes” — assim nenhuma conta aparece sem você ter conferido.
              </p>
            )}
            {!draft.id && (
              <>
                <Switch
                  checked={draft.alreadyPaid}
                  onChange={(v) => setDraft({ ...draft, alreadyPaid: v })}
                  label="Esta conta já foi paga"
                />
                {draft.alreadyPaid && (
                  <Switch
                    checked={draft.fromCashRegister}
                    onChange={(v) => setDraft({ ...draft, fromCashRegister: v })}
                    label="O dinheiro saiu do caixa (gera sangria)"
                  />
                )}
              </>
            )}
            <div className="field">
              <label>Observação (opcional)
                <input
                  className="input"
                  value={draft.notes}
                  placeholder="ex.: contrato até dez/2027"
                  onChange={(ev) => setDraft({ ...draft, notes: ev.target.value })}
                />
              </label>
            </div>
            {formError && <div className="error">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
