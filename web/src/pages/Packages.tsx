// Pacotes pré-pagos (V34): "10 cortes por R$ 300". Diferente do clube (recorrente): o cliente
// paga de uma vez e vai gastando. A venda entra no caixa aberto; a baixa é automática quando a
// comanda do cliente (com telefone) é fechada.
import { useEffect, useState } from "react";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { Modal } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type {
  CashPaymentMethod,
  Customer,
  CustomerPackage,
  PageMeta,
  ServiceOffering,
  ServicePackage,
} from "../api/types";
import { dateBr, money } from "../lib/format";

interface Draft {
  id: string | null;
  name: string;
  serviceId: string;
  quantity: string;
  price: string;
  validityDays: string;
}

const EMPTY: Draft = { id: null, name: "", serviceId: "", quantity: "10", price: "", validityDays: "90" };

const PAYMENT_METHODS: { value: CashPaymentMethod; label: string }[] = [
  { value: "DINHEIRO", label: "Dinheiro" },
  { value: "PIX", label: "PIX" },
  { value: "DEBITO", label: "Débito" },
  { value: "CREDITO", label: "Crédito" },
];

const STATUS_BADGE: Record<CustomerPackage["status"], { cls: string; label: string }> = {
  ATIVO: { cls: "badge-green", label: "Ativo" },
  ESGOTADO: { cls: "badge-muted", label: "Esgotado" },
  VENCIDO: { cls: "badge-muted", label: "Vencido" },
};

export function Packages() {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const [showAll, setShowAll] = useState(false);
  const refresh = () => setVersion((v) => v + 1);

  const catalog = useApi<ServicePackage[]>(() => api.get("/api/admin/packages"), [version]);
  const balances = useApi<CustomerPackage[]>(
    () => api.get(`/api/admin/packages/balances${showAll ? "?all=true" : ""}`),
    [version, showAll],
  );
  const services = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);

  const [draft, setDraft] = useState<Draft | null>(null);
  const [selling, setSelling] = useState<ServicePackage | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const save = async () => {
    if (!draft) return;
    const quantity = Number(draft.quantity);
    const price = Number(draft.price.replace(",", "."));
    const validityDays = Number(draft.validityDays);
    if (!Number.isInteger(quantity) || quantity <= 0) return setFormError("A quantidade de usos deve ser maior que zero.");
    if (!Number.isFinite(price) || price <= 0) return setFormError("O preço do pacote deve ser maior que zero.");
    if (!Number.isInteger(validityDays) || validityDays <= 0) return setFormError("A validade deve ser de pelo menos 1 dia.");
    setSaving(true);
    setFormError(null);
    const body = {
      name: draft.name.trim(),
      serviceId: draft.serviceId || null,
      quantity,
      price,
      validityDays,
    };
    try {
      if (draft.id) await api.put(`/api/admin/packages/${draft.id}`, body);
      else await api.post("/api/admin/packages", body);
      toast.success(draft.id ? "Pacote atualizado ✓" : "Pacote criado ✓");
      setDraft(null);
      refresh();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao salvar o pacote.");
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (p: ServicePackage) => {
    try {
      await api.put(`/api/admin/packages/${p.id}/active`, { active: !p.active });
      toast.success(p.active ? "Pacote desativado ✓" : "Pacote reativado ✓");
      refresh();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao alterar o pacote.");
    }
  };

  const edit = (p: ServicePackage) => {
    setFormError(null);
    setDraft({
      id: p.id,
      name: p.name,
      serviceId: p.serviceId ?? "",
      quantity: String(p.quantity),
      price: String(p.price),
      validityDays: String(p.validityDays),
    });
  };

  const draftUnit = (() => {
    if (!draft) return null;
    const q = Number(draft.quantity);
    const p = Number(draft.price.replace(",", "."));
    return q > 0 && p > 0 ? p / q : null;
  })();

  return (
    <Layout
      title="Pacotes"
      actions={
        <button className="btn btn-primary btn-sm" onClick={() => { setFormError(null); setDraft({ ...EMPTY }); }}>
          + Novo pacote
        </button>
      }
    >
      <div className="card">
        <div className="card-head">
          <h3>Catálogo de pacotes</h3>
        </div>
        {catalog.loading ? (
          <SkeletonRows rows={3} />
        ) : catalog.error ? (
          <ErrorState message={catalog.error} onRetry={catalog.reload} />
        ) : !catalog.data || catalog.data.length === 0 ? (
          <EmptyState
            icon="🎫"
            title="Nenhum pacote criado."
            hint="Ex.: 10 cortes por R$ 300, válidos por 90 dias. O cliente paga de uma vez e cada corte sai a R$ 0,00 na comanda."
            action={<button className="btn btn-primary" onClick={() => setDraft({ ...EMPTY })}>+ Criar pacote</button>}
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Pacote</th>
                  <th>Cobre</th>
                  <th>Usos</th>
                  <th>Preço</th>
                  <th>Validade</th>
                  <th>Situação</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {catalog.data.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 600 }}>{p.name}</td>
                    <td className="muted">{p.serviceName ?? "Qualquer serviço"}</td>
                    <td className="mono">{p.quantity}</td>
                    <td className="mono">
                      {money(p.price)}
                      <div className="faint" style={{ fontSize: 11.5 }}>{money(p.unitValue)} por uso</div>
                    </td>
                    <td className="muted">{p.validityDays} dias</td>
                    <td>
                      {p.active ? <span className="badge badge-green">Ativo</span> : <span className="badge badge-muted">Desativado</span>}
                    </td>
                    <td className="right" style={{ whiteSpace: "nowrap" }}>
                      {p.active && (
                        <button className="btn btn-primary btn-sm" onClick={() => setSelling(p)}>Vender</button>
                      )}{" "}
                      <button className="btn btn-ghost btn-sm" onClick={() => edit(p)}>Editar</button>{" "}
                      <button className="btn btn-ghost btn-sm" onClick={() => toggleActive(p)}>
                        {p.active ? "Desativar" : "Reativar"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Saldos dos clientes</h3>
          <div className="chip-row">
            <button className={`chip-btn ${!showAll ? "active" : ""}`} onClick={() => setShowAll(false)}>Com saldo</button>
            <button className={`chip-btn ${showAll ? "active" : ""}`} onClick={() => setShowAll(true)}>Todos</button>
          </div>
        </div>
        {balances.loading ? (
          <SkeletonRows rows={3} />
        ) : balances.error ? (
          <ErrorState message={balances.error} onRetry={balances.reload} />
        ) : !balances.data || balances.data.length === 0 ? (
          <div className="card-pad faint">
            {showAll ? "Nenhum pacote vendido ainda." : "Nenhum cliente com pacote ativo."}
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Cliente</th>
                  <th>Pacote</th>
                  <th>Saldo</th>
                  <th>Vence</th>
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {balances.data.map((b) => (
                  <tr key={b.id}>
                    <td>
                      {b.customerName || "Sem nome"}
                      {b.customerPhone && <div className="faint mono" style={{ fontSize: 11.5 }}>{b.customerPhone}</div>}
                    </td>
                    <td>
                      {b.packageName}
                      <div className="faint" style={{ fontSize: 11.5 }}>{b.serviceName ?? "qualquer serviço"} · {money(b.price)}</div>
                    </td>
                    <td className="mono">{b.remainingUses} / {b.totalUses}</td>
                    <td className="muted">{dateBr(b.expiresAt)}</td>
                    <td><span className={`badge ${STATUS_BADGE[b.status].cls}`}>{STATUS_BADGE[b.status].label}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="card-pad faint" style={{ fontSize: 12 }}>
          A baixa é automática: ao fechar a comanda de um cliente com pacote (informe o telefone ao
          abrir a comanda), o serviço coberto sai a R$ 0,00 e um uso é consumido.
        </div>
      </div>

      {draft && (
        <Modal
          title={draft.id ? "Editar pacote" : "Novo pacote"}
          onClose={() => setDraft(null)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setDraft(null)} disabled={saving}>Cancelar</button>
              <button className="btn btn-primary" onClick={save} disabled={saving || !draft.name.trim()}>
                {saving ? "Salvando…" : draft.id ? "Salvar" : "Criar pacote"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Nome
                <input
                  className="input"
                  value={draft.name}
                  placeholder="ex.: 10 cortes"
                  onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                />
              </label>
            </div>
            <div className="field">
              <label>Serviço coberto
                <select
                  className="input"
                  value={draft.serviceId}
                  onChange={(e) => setDraft({ ...draft, serviceId: e.target.value })}
                >
                  <option value="">Qualquer serviço</option>
                  {(services.data ?? []).filter((s) => s.active || s.id === draft.serviceId).map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </label>
            </div>
            <div className="form-grid">
              <div className="field">
                <label>Quantidade de usos
                  <input
                    className="input"
                    inputMode="numeric"
                    value={draft.quantity}
                    onChange={(e) => setDraft({ ...draft, quantity: e.target.value })}
                  />
                </label>
              </div>
              <div className="field">
                <label>Preço do pacote (R$)
                  <input
                    className="input"
                    inputMode="decimal"
                    value={draft.price}
                    onChange={(e) => setDraft({ ...draft, price: e.target.value })}
                  />
                </label>
              </div>
            </div>
            <div className="field">
              <label>Validade (dias, a partir da venda)
                <input
                  className="input"
                  inputMode="numeric"
                  value={draft.validityDays}
                  onChange={(e) => setDraft({ ...draft, validityDays: e.target.value })}
                />
              </label>
            </div>
            {draftUnit !== null && (
              <span className="faint" style={{ fontSize: 12 }}>
                Cada uso vale <strong>{money(draftUnit)}</strong> — é sobre esse valor que o barbeiro
                recebe comissão quando atende com o pacote.
              </span>
            )}
            {draft.id && (
              <span className="faint" style={{ fontSize: 12 }}>
                Mudar o pacote não altera os que já foram vendidos: cada cliente mantém o que pagou.
              </span>
            )}
            {formError && <div className="error" role="alert">{formError}</div>}
          </div>
        </Modal>
      )}

      {selling && (
        <SellPackageModal pkg={selling} onClose={() => setSelling(null)} onSold={refresh} />
      )}
    </Layout>
  );
}

function SellPackageModal({
  pkg,
  onClose,
  onSold,
}: {
  pkg: ServicePackage;
  onClose: () => void;
  onSold: () => void;
}) {
  const toast = useToast();
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [method, setMethod] = useState<CashPaymentMethod>("PIX");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(q.trim()), 300);
    return () => clearTimeout(id);
  }, [q]);

  const results = useApi<PageMeta<Customer>>(
    () => api.get(`/api/admin/customers?size=8&q=${encodeURIComponent(debounced)}`),
    [debounced],
  );

  const sell = async () => {
    if (!customer) return;
    setSaving(true);
    setError(null);
    try {
      await api.post(`/api/admin/packages/${pkg.id}/sell`, { customerId: customer.id, paymentMethod: method });
      toast.success(`Pacote vendido ✓ ${money(pkg.price)} no caixa.`);
      onSold();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao vender o pacote.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={`Vender ${pkg.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={saving}>Cancelar</button>
          <button className="btn btn-primary" onClick={sell} disabled={saving || !customer}>
            {saving ? "Registrando…" : `Vender ${money(pkg.price)}`}
          </button>
        </>
      }
    >
      <div className="stack">
        <div className="field">
          <label>Cliente
            <input
              className="input"
              placeholder="Buscar por nome ou telefone…"
              value={q}
              onChange={(e) => { setQ(e.target.value); setCustomer(null); }}
            />
          </label>
        </div>
        {customer ? (
          <div className="card-pad">
            <strong>{customer.name || "Sem nome"}</strong> <span className="mono faint">{customer.phone}</span>{" "}
            <button className="btn btn-ghost btn-sm" onClick={() => setCustomer(null)}>Trocar</button>
          </div>
        ) : (
          <div className="stack" style={{ gap: 4 }}>
            {(results.data?.items ?? []).map((c) => (
              <button key={c.id} className="btn btn-ghost btn-sm" style={{ justifyContent: "flex-start" }} onClick={() => setCustomer(c)}>
                {c.name || "Sem nome"} · <span className="mono">{c.phone}</span>
              </button>
            ))}
            {results.data && results.data.items.length === 0 && (
              <span className="faint" style={{ fontSize: 12 }}>Nenhum cliente encontrado.</span>
            )}
          </div>
        )}
        <div className="field">
          <label>Forma de pagamento
            <select className="input" value={method} onChange={(e) => setMethod(e.target.value as CashPaymentMethod)}>
              {PAYMENT_METHODS.map((m) => (
                <option key={m.value} value={m.value}>{m.label}</option>
              ))}
            </select>
          </label>
        </div>
        <span className="faint" style={{ fontSize: 12 }}>
          {pkg.quantity} usos · {pkg.serviceName ?? "qualquer serviço"} · válido por {pkg.validityDays} dias.
          A venda entra no caixa aberto — sem caixa aberto, ela é recusada.
        </span>
        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
