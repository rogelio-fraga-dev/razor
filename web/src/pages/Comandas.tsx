// Comandas do atendimento (V22): serviços + produtos na mesma conta, fechada de uma vez.
// Fechar exige caixa aberto — a comanda lança UM movimento com o total.
import { useState } from "react";
import { Layout } from "../components/Layout";
import { Modal } from "../components/Modal";
import { EmptyState } from "../components/EmptyState";
import { SkeletonRows } from "../components/Skeleton";
import { ComandaCard } from "../components/ComandaCard";
import { useToast } from "../components/Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { Comanda, Product, Professional, ServiceOffering } from "../api/types";
import { money } from "../lib/format";

const TIME_FMT = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

export function Comandas() {
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const [opening, setOpening] = useState(false);
  const [name, setName] = useState("");
  const [professionalId, setProfessionalId] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = useApi<Comanda[]>(() => api.get("/api/admin/comandas"), [version]);
  const today = useApi<Comanda[]>(() => api.get("/api/admin/comandas/today"), [version]);
  const services = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const products = useApi<Product[]>(() => api.get("/api/admin/products"), []);
  const professionals = useApi<Professional[]>(() => api.get("/api/admin/professionals"), []);

  const refresh = () => setVersion((v) => v + 1);

  const openComanda = async () => {
    setSaving(true);
    setFormError(null);
    try {
      await api.post("/api/admin/comandas", {
        customerName: name.trim() || null,
        customerPhone: null,
        professionalId: professionalId || null,
      });
      toast.success("Comanda aberta ✓");
      setName("");
      setProfessionalId("");
      setOpening(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : "Erro ao abrir a comanda.");
    } finally {
      setSaving(false);
    }
  };

  const closedToday = (today.data ?? []).filter((c) => c.status !== "ABERTA");
  const closedTotal = closedToday
    .filter((c) => c.status === "FECHADA")
    .reduce((sum, c) => sum + (c.total ?? 0), 0);

  return (
    <Layout
      title="Comandas"
      actions={
        <button className="btn btn-primary btn-sm" onClick={() => setOpening(true)}>
          + Abrir comanda
        </button>
      }
    >
      {open.loading ? (
        <SkeletonRows rows={3} />
      ) : open.error ? (
        <div className="card error">{open.error}</div>
      ) : !open.data || open.data.length === 0 ? (
        <EmptyState
          icon="🧾"
          title="Nenhuma comanda aberta."
          hint="Abra uma comanda para juntar os serviços e produtos do atendimento numa conta só, e cobrar de uma vez no fim."
          action={<button className="btn btn-primary" onClick={() => setOpening(true)}>+ Abrir comanda</button>}
        />
      ) : (
        <div className="stack" style={{ gap: 16 }}>
          {open.data.map((c) => (
            <ComandaCard
              key={c.id}
              comanda={c}
              services={services.data ?? []}
              products={products.data ?? []}
              onChanged={refresh}
            />
          ))}
        </div>
      )}

      {/* Fechadas e canceladas do dia — o histórico logo abaixo, sem trocar de tela */}
      <div className="card">
        <div className="card-head">
          <h3>Fechadas hoje</h3>
          {closedTotal > 0 && <span className="badge badge-green mono">{money(closedTotal)}</span>}
        </div>
        {closedToday.length === 0 ? (
          <div className="card-pad faint">Nenhuma comanda fechada hoje.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Aberta</th>
                  <th>Cliente</th>
                  <th>Barbeiro</th>
                  <th>Itens</th>
                  <th>Pagamento</th>
                  <th style={{ textAlign: "right" }}>Total</th>
                </tr>
              </thead>
              <tbody>
                {closedToday.map((c) => (
                  <tr key={c.id}>
                    <td className="mono">{TIME_FMT.format(new Date(c.openedAt))}</td>
                    <td style={{ fontWeight: 550 }}>{c.customerName || "—"}</td>
                    <td className="muted">{c.professionalName ?? "—"}</td>
                    <td className="muted">{c.items.length}</td>
                    <td>
                      {c.status === "CANCELADA" ? (
                        <span className="badge badge-muted">Cancelada</span>
                      ) : (
                        <span className="muted">{c.paymentMethod}</span>
                      )}
                    </td>
                    <td className="num">
                      {c.status === "CANCELADA" ? <span className="faint">—</span> : money(c.total)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {opening && (
        <Modal
          title="Abrir comanda"
          onClose={() => setOpening(false)}
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setOpening(false)} disabled={saving}>
                Cancelar
              </button>
              <button className="btn btn-primary" onClick={openComanda} disabled={saving}>
                {saving ? "Abrindo…" : "Abrir"}
              </button>
            </>
          }
        >
          <div className="stack">
            <div className="field">
              <label>Cliente (opcional)</label>
              <input
                className="input"
                value={name}
                placeholder="ex.: João"
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="field">
              <label>Barbeiro (opcional)</label>
              <select
                className="input"
                value={professionalId}
                onChange={(e) => setProfessionalId(e.target.value)}
              >
                <option value="">Sem barbeiro — produtos não geram comissão</option>
                {(professionals.data ?? []).filter((p) => p.active).map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
              Os itens entram depois, um a um. Ao fechar, a comanda lança <strong>um único</strong>{" "}
              movimento no caixa com o total — por isso o caixa precisa estar aberto.
            </p>
            {formError && <div className="error">{formError}</div>}
          </div>
        </Modal>
      )}
    </Layout>
  );
}
