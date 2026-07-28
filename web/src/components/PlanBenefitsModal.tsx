// Benefícios de um plano (V27): o que o assinante ganha em cada serviço/produto.
// Espelha o que o cliente vê no app: "100% OFF · De R$ 35,00 · Por R$ 0,00 · seg, ter, qua".
import { useState } from "react";
import { Modal } from "./Modal";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { PlanBenefit, Product, ServiceOffering, SubscriptionPlan } from "../api/types";
import { money } from "../lib/format";

const DIAS = [
  { value: "1", label: "Seg" },
  { value: "2", label: "Ter" },
  { value: "3", label: "Qua" },
  { value: "4", label: "Qui" },
  { value: "5", label: "Sex" },
  { value: "6", label: "Sáb" },
  { value: "7", label: "Dom" },
];

/** "1,2,3" → "Seg, Ter, Qua". Vazio = todos os dias. */
function weekdaysLabel(csv: string | null): string {
  if (!csv) return "todos os dias";
  return csv
    .split(",")
    .map((d) => DIAS.find((x) => x.value === d.trim())?.label)
    .filter(Boolean)
    .join(", ");
}

export function PlanBenefitsModal({
  plan,
  onClose,
}: {
  plan: SubscriptionPlan;
  onClose: () => void;
}) {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const benefits = useApi<PlanBenefit[]>(() => api.get(`/api/admin/plans/${plan.id}/benefits`), [version]);
  const services = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const products = useApi<Product[]>(() => api.get("/api/admin/products"), []);

  const [target, setTarget] = useState("SERVICO:");
  const [discount, setDiscount] = useState("10");
  const [quota, setQuota] = useState("");
  const [weekdays, setWeekdays] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const nameOfTarget = (b: PlanBenefit): string => {
    if (b.targetId === null) {
      return b.targetType === "SERVICO" ? "Todos os serviços" : "Todos os produtos";
    }
    const list = b.targetType === "SERVICO" ? services.data ?? [] : products.data ?? [];
    return list.find((x) => x.id === b.targetId)?.name ?? "(item removido do catálogo)";
  };

  const priceOfTarget = (b: PlanBenefit): number | null => {
    if (b.targetId === null) return null;
    const list = b.targetType === "SERVICO" ? services.data ?? [] : products.data ?? [];
    return list.find((x) => x.id === b.targetId)?.price ?? null;
  };

  const save = async () => {
    const pct = Number(discount.replace(",", "."));
    if (!Number.isFinite(pct) || pct < 0 || pct > 100) {
      setError("O desconto deve estar entre 0 e 100.");
      return;
    }
    const qty = quota.trim() === "" ? null : Number(quota);
    if (qty !== null && (!Number.isInteger(qty) || qty <= 0)) {
      setError("A cota mensal deve ser um número inteiro maior que zero.");
      return;
    }
    const [targetType, targetId] = target.split(":");
    setBusy(true);
    setError(null);
    try {
      await api.put(`/api/admin/plans/${plan.id}/benefits`, {
        targetType,
        targetId: targetId || null,
        discountPercent: pct,
        monthlyQuota: qty,
        // Vazio = todos os dias; o backend guarda null.
        weekdays: weekdays.length === 0 ? null : weekdays.join(","),
      });
      toast.success("Benefício salvo ✓");
      setVersion((v) => v + 1);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao salvar o benefício.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (benefitId: string) => {
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/admin/plans/${plan.id}/benefits/${benefitId}`);
      setVersion((v) => v + 1);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao remover.");
    } finally {
      setBusy(false);
    }
  };

  const toggleDay = (value: string) =>
    setWeekdays((prev) => (prev.includes(value) ? prev.filter((d) => d !== value) : [...prev, value]));

  return (
    <Modal title={`Benefícios — ${plan.name}`} onClose={onClose}>
      <div className="stack">
        <p className="muted" style={{ marginTop: 0, fontSize: 13.5 }}>
          É isto que o assinante vê no preço: <strong>De</strong> valor cheio{" "}
          <strong>Por</strong> valor com desconto. Benefício de um item específico ganha do
          benefício geral do tipo.
        </p>

        {/* Benefícios já cadastrados */}
        {benefits.loading ? (
          <div className="muted">Carregando…</div>
        ) : benefits.error ? (
          <div className="error">{benefits.error}</div>
        ) : !benefits.data || benefits.data.length === 0 ? (
          <div className="faint" style={{ fontSize: 13 }}>
            Nenhum benefício ainda — este plano não dá desconto nenhum.
          </div>
        ) : (
          <div className="stack" style={{ gap: 8 }}>
            {benefits.data.map((b) => {
              const base = priceOfTarget(b);
              return (
                <div
                  key={b.id}
                  className="row between"
                  style={{ padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}
                >
                  <div className="grow">
                    <div style={{ fontWeight: 550 }}>
                      {nameOfTarget(b)}
                      <span className={`badge ${b.free ? "badge-green" : "badge-muted"}`} style={{ marginLeft: 8 }}>
                        {b.free ? "GRÁTIS" : `${b.discountPercent}% OFF`}
                      </span>
                    </div>
                    <div className="faint" style={{ fontSize: 12 }}>
                      {base !== null && (
                        <>
                          De <s>{money(base)}</s> por{" "}
                          <strong>{money(base * (1 - b.discountPercent / 100))}</strong> ·{" "}
                        </>
                      )}
                      {b.monthlyQuota === null ? "ilimitado" : `${b.monthlyQuota}x por mês`} ·{" "}
                      {weekdaysLabel(b.weekdays)}
                    </div>
                  </div>
                  <button className="btn btn-danger btn-sm" onClick={() => remove(b.id)} disabled={busy}>
                    Remover
                  </button>
                </div>
              );
            })}
          </div>
        )}

        <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "4px 0" }} />

        {/* Novo benefício */}
        <div className="field">
          <label>Item</label>
          <select className="input" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="SERVICO:">Todos os serviços</option>
            {(services.data ?? []).filter((s) => s.active).map((s) => (
              <option key={s.id} value={`SERVICO:${s.id}`}>{s.name} — {money(s.price)}</option>
            ))}
            <option value="PRODUTO:">Todos os produtos</option>
            {(products.data ?? []).filter((p) => p.active).map((p) => (
              <option key={p.id} value={`PRODUTO:${p.id}`}>{p.name} — {money(p.price)}</option>
            ))}
          </select>
        </div>

        <div className="form-grid">
          <div className="field">
            <label>Desconto (%)</label>
            <input className="input" inputMode="decimal" value={discount} onChange={(e) => setDiscount(e.target.value)} />
            <span className="faint" style={{ fontSize: 12 }}>100 = grátis.</span>
          </div>
          <div className="field">
            <label>Cota mensal</label>
            <input
              className="input"
              inputMode="numeric"
              value={quota}
              placeholder="vazio = ilimitado"
              onChange={(e) => setQuota(e.target.value)}
            />
          </div>
        </div>

        <div className="field">
          <label>Dias em que vale</label>
          <div className="row wrap" style={{ gap: 6 }}>
            {DIAS.map((d) => (
              <button
                key={d.value}
                className={`btn btn-sm ${weekdays.includes(d.value) ? "btn-primary" : "btn-ghost"}`}
                onClick={() => toggleDay(d.value)}
              >
                {d.label}
              </button>
            ))}
          </div>
          <span className="faint" style={{ fontSize: 12 }}>
            Nenhum selecionado = vale todos os dias. Selecionar Seg/Ter/Qua reproduz o plano de
            dia fraco.
          </span>
        </div>

        <button className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? "Salvando…" : "Salvar benefício"}
        </button>

        {error && <div className="error">{error}</div>}
      </div>
    </Modal>
  );
}
