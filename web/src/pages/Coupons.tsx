// Cupons de desconto (V29): campanhas do dono e as recompensas emitidas pelo cartão fidelidade.
// Antes desta tela, o cupom da fidelidade era só um texto enviado no WhatsApp — o caixa não
// tinha como validar e nada impedia usar duas vezes.
import { useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import { Pagination } from "../components/Pagination";
import type { Coupon, CouponDiscountType, PageMeta } from "../api/types";
import { dateBr, money, percent, todayIso } from "../lib/format";

interface Draft {
  code: string;
  description: string;
  discountType: CouponDiscountType;
  discountValue: string;
  validUntil: string;
  maxUses: string;
  minAmount: string;
  customerPhone: string;
}

const EMPTY: Draft = {
  code: "",
  description: "",
  discountType: "PERCENT",
  discountValue: "10",
  validUntil: "",
  maxUses: "",
  minAmount: "",
  customerPhone: "",
};

export function Coupons() {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const [page, setPage] = useState(0);
  const { data: pageData, loading, error, reload } = useApi<PageMeta<Coupon>>(
    () => api.get(`/api/admin/coupons?page=${page}`),
    [page, version],
  );
  const data = pageData?.items;
  const discount = useApi<{ total: number }>(() => api.get("/api/admin/coupons/total-discount"), [version]);

  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const save = async () => {
    if (!draft) return;
    const value = Number(draft.discountValue.replace(",", "."));
    if (!Number.isFinite(value) || value <= 0) {
      setFormError("O desconto deve ser maior que zero.");
      return;
    }
    if (draft.discountType === "PERCENT" && value > 100) {
      setFormError("O desconto percentual não pode passar de 100.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await api.post("/api/admin/coupons", {
        code: draft.code.trim(),
        description: draft.description.trim() || null,
        discountType: draft.discountType,
        discountValue: value,
        validFrom: null,
        validUntil: draft.validUntil || null,
        maxUses: draft.maxUses.trim() === "" ? null : Number(draft.maxUses),
        minAmount: draft.minAmount.trim() === "" ? null : Number(draft.minAmount.replace(",", ".")),
        customerPhone: draft.customerPhone.trim() || null,
      });
      toast.success(`Cupom ${draft.code.trim().toUpperCase()} criado ✓`);
      setDraft(null);
      setVersion((v) => v + 1);
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao criar o cupom.");
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (c: Coupon) => {
    try {
      await api.put(`/api/admin/coupons/${c.id}/active`, { active: !c.active });
      setVersion((v) => v + 1);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao alterar o cupom.");
    }
  };

  const valueOf = (c: Coupon) =>
    c.discountType === "PERCENT" ? percent(c.discountValue) : money(c.discountValue);

  return (
    <Layout
      title="Cupons"
      actions={
        <button className="btn btn-primary btn-sm" onClick={() => { setFormError(null); setDraft({ ...EMPTY }); }}>
          + Novo cupom
        </button>
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Cupons</h3>
          {discount.data && discount.data.total > 0 && (
            <span className="badge badge-amber mono" title="Desconto concedido no mês">
              {money(discount.data.total)} em descontos no mês
            </span>
          )}
        </div>

        {loading ? (
          <SkeletonRows rows={3} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon="🎟️"
            title="Nenhum cupom criado."
            hint="Crie uma campanha (ex.: VOLTA10 com 10% off) ou deixe o cartão fidelidade emitir as recompensas sozinho."
            action={
              <button className="btn btn-primary" onClick={() => setDraft({ ...EMPTY })}>+ Criar cupom</button>
            }
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table table-cards">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Desconto</th>
                  <th>Origem</th>
                  <th>Validade</th>
                  <th>Usos</th>
                  <th>Situação</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((c) => (
                  <tr key={c.id}>
                    <td className="tc-head">
                      <span className="mono" style={{ fontWeight: 600 }}>{c.code}</span>
                      {c.description && (
                        <div className="faint" style={{ fontSize: 11.5 }}>{c.description}</div>
                      )}
                      {c.customerPhone && (
                        <div className="faint" style={{ fontSize: 11.5 }}>pessoal · {c.customerPhone}</div>
                      )}
                    </td>
                    <td className="mono" data-label="Desconto">{valueOf(c)}</td>
                    <td data-label="Origem">
                      {c.origin === "FIDELIDADE" ? (
                        <span className="badge badge-plan">Fidelidade</span>
                      ) : c.origin === "ANIVERSARIO" ? (
                        <span className="badge badge-plan">Aniversário</span>
                      ) : (
                        <span className="faint">Campanha</span>
                      )}
                    </td>
                    <td className="muted" data-label="Validade">
                      {c.validUntil ? `até ${dateBr(c.validUntil)}` : "sem prazo"}
                    </td>
                    <td className="muted" data-label="Usos">
                      {c.usedCount}
                      {c.maxUses !== null ? ` / ${c.maxUses}` : ""}
                    </td>
                    <td data-label="Situação">
                      {c.usable ? (
                        <span className="badge badge-green">Válido</span>
                      ) : c.active ? (
                        <span className="badge badge-muted">Esgotado/vencido</span>
                      ) : (
                        <span className="badge badge-muted">Desativado</span>
                      )}
                    </td>
                    <td className="right tc-actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => toggleActive(c)}>
                        {c.active ? "Desativar" : "Reativar"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {pageData && (
          <Pagination page={pageData} onChange={setPage} label="cupons" busy={loading} />
        )}
      </div>

      {draft && (
        <Modal
          title="Novo cupom"
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={save} disabled={saving || !draft.code.trim()}>
                {saving ? "Criando…" : "Criar cupom"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Código
              <input
                className="input mono"
                value={draft.code}
                placeholder="ex.: VOLTA10"
                onChange={(e) => setDraft({ ...draft, code: e.target.value })}
              />
            </label>
              <span className="faint" style={{ fontSize: 12 }}>
                É o que o cliente informa no caixa. Vira maiúsculo automaticamente.
              </span>
            </div>

            <div className="field">
              <label>Descrição (opcional)
              <input
                className="input"
                value={draft.description}
                placeholder="ex.: campanha de reativação"
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
              />
            </label>
            </div>

            <div className="form-grid">
              <div className="field">
                <label>Tipo
                <select
                  className="input"
                  value={draft.discountType}
                  onChange={(e) => setDraft({ ...draft, discountType: e.target.value as CouponDiscountType })}
                >
                  <option value="PERCENT">Percentual (%)</option>
                  <option value="FIXED">Valor fixo (R$)</option>
                </select>
            </label>
              </div>
              <div className="field">
                <label>Desconto
                <input
                  className="input"
                  inputMode="decimal"
                  value={draft.discountValue}
                  onChange={(e) => setDraft({ ...draft, discountValue: e.target.value })}
                />
            </label>
              </div>
            </div>

            <div className="form-grid">
              <div className="field">
                <label>Válido até (opcional)
                <input
                  className="input"
                  type="date"
                  min={todayIso()}
                  value={draft.validUntil}
                  onChange={(e) => setDraft({ ...draft, validUntil: e.target.value })}
                />
            </label>
              </div>
              <div className="field">
                <label>Limite de usos
                <input
                  className="input"
                  inputMode="numeric"
                  value={draft.maxUses}
                  placeholder="vazio = ilimitado"
                  onChange={(e) => setDraft({ ...draft, maxUses: e.target.value })}
                />
            </label>
              </div>
            </div>

            <div className="form-grid">
              <div className="field">
                <label>Valor mínimo da conta
                <input
                  className="input"
                  inputMode="decimal"
                  value={draft.minAmount}
                  placeholder="vazio = sem mínimo"
                  onChange={(e) => setDraft({ ...draft, minAmount: e.target.value })}
                />
            </label>
              </div>
              <div className="field">
                <label>Telefone do cliente
                <input
                  className="input"
                  value={draft.customerPhone}
                  placeholder="vazio = qualquer cliente"
                  onChange={(e) => setDraft({ ...draft, customerPhone: e.target.value })}
                />
            </label>
              </div>
            </div>
            <span className="faint" style={{ fontSize: 12 }}>
              Com telefone preenchido o cupom é <strong>pessoal</strong>: só aquele cliente usa.
              Vazio, é campanha aberta.
            </span>

            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
