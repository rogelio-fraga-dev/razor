// Editor do catálogo de serviços em linhas (nome | preço | duração), usado na configuração do
// bot. Grava direto no catálogo real (`/api/admin/services`): é a MESMA tabela que o bot usa para
// responder preço e calcular horário, a agenda usa para a duração e a comanda para o valor.
// Antes este passo era um texto livre ("Corte — R$ 40") que só o menu lia — bot e painel podiam
// dizer preços diferentes.
import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import type { ServiceOffering } from "../api/types";
import { useApi } from "../lib/useApi";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";

interface Row {
  /** Nulo = linha nova, ainda não gravada. */
  id: string | null;
  name: string;
  price: string;
  duration: string;
  dirty: boolean;
}

function toRow(s: ServiceOffering): Row {
  return {
    id: s.id,
    name: s.name,
    price: s.price.toFixed(2).replace(".", ","),
    duration: String(s.durationMinutes),
    dirty: false,
  };
}

function parsePrice(v: string): number | null {
  const n = Number(v.replace(/\./g, "").replace(",", ".").trim());
  return v.trim() !== "" && Number.isFinite(n) && n >= 0 ? n : null;
}

export function ServiceCatalogEditor({ onCountChange }: { onCountChange?: (n: number) => void }) {
  const { data, loading, error, reload } = useApi<ServiceOffering[]>(() => api.get("/api/admin/services"), []);
  const toast = useToast();
  const [rows, setRows] = useState<Row[]>([]);
  const [busy, setBusy] = useState<number | null>(null);

  useEffect(() => {
    if (!data) return;
    const ativos = data.filter((s) => s.active).map(toRow);
    setRows(ativos.length > 0 ? ativos : [{ id: null, name: "", price: "", duration: "30", dirty: true }]);
    onCountChange?.(ativos.length);
  }, [data, onCountChange]);

  const patch = (i: number, p: Partial<Row>) =>
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...p, dirty: true } : r)));

  const add = () => setRows((rs) => [...rs, { id: null, name: "", price: "", duration: "30", dirty: true }]);

  const save = async (i: number) => {
    const r = rows[i];
    const price = parsePrice(r.price);
    const duration = Number(r.duration);
    if (r.name.trim() === "") return toast.error("Dê um nome ao serviço.");
    if (price == null) return toast.error("Preço inválido (ex.: 40 ou 39,90).");
    if (!Number.isInteger(duration) || duration <= 0) return toast.error("Duração em minutos (ex.: 30).");
    setBusy(i);
    try {
      const body = { name: r.name.trim(), price, durationMinutes: duration, active: true };
      if (r.id) await api.put(`/api/admin/services/${r.id}`, body);
      else await api.post("/api/admin/services", body);
      toast.success("Serviço salvo ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar o serviço.");
    } finally {
      setBusy(null);
    }
  };

  const remove = async (i: number) => {
    const r = rows[i];
    if (!r.id) {
      setRows((rs) => rs.filter((_, j) => j !== i));
      return;
    }
    const price = parsePrice(r.price) ?? 0;
    setBusy(i);
    try {
      // Desativa (não apaga): agendamentos e comandas antigas continuam apontando para ele.
      await api.put(`/api/admin/services/${r.id}`, {
        name: r.name.trim() || "Serviço",
        price,
        durationMinutes: Number(r.duration) || 30,
        active: false,
      });
      toast.success("Serviço removido ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao remover o serviço.");
    } finally {
      setBusy(null);
    }
  };

  if (loading && !data) return <SkeletonRows rows={3} />;
  if (error) return <div className="error" role="alert">{error}</div>;

  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="svc-editor-head muted" aria-hidden="true">
        <span>Serviço</span>
        <span>Preço (R$)</span>
        <span>Duração (min)</span>
        <span />
      </div>
      {rows.map((r, i) => (
        <div className="svc-editor-row" key={r.id ?? `novo-${i}`}>
          <input
            className="input"
            aria-label="Nome do serviço"
            placeholder="Ex.: Corte"
            value={r.name}
            onChange={(e) => patch(i, { name: e.target.value })}
          />
          <input
            className="input"
            aria-label="Preço em reais"
            inputMode="decimal"
            placeholder="40,00"
            value={r.price}
            onChange={(e) => patch(i, { price: e.target.value.replace(/[^\d,.]/g, "") })}
          />
          <input
            className="input"
            aria-label="Duração em minutos"
            inputMode="numeric"
            placeholder="30"
            value={r.duration}
            onChange={(e) => patch(i, { duration: e.target.value.replace(/\D/g, "") })}
          />
          <div className="row" style={{ gap: 6 }}>
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={() => save(i)}
              disabled={busy === i || !r.dirty}
            >
              {busy === i ? "…" : "Salvar"}
            </button>
            <button
              type="button"
              className="btn btn-danger btn-sm"
              onClick={() => remove(i)}
              disabled={busy === i}
              aria-label={`Remover ${r.name || "linha"}`}
            >
              ✕
            </button>
          </div>
        </div>
      ))}
      <div>
        <button type="button" className="btn btn-ghost btn-sm" onClick={add}>
          + adicionar serviço
        </button>
      </div>
      <p className="muted" style={{ fontSize: 13, margin: 0 }}>
        Combos entram como um serviço próprio (ex.: “Corte + Barba” por R$ 60): o bot oferece o combo
        quando o cliente pede os serviços separados e diz quanto ele economiza.
      </p>
    </div>
  );
}
