import { useEffect, useState } from "react";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { BookingLimit } from "../api/types";

/**
 * Limite de horários futuros por cliente (V36). Card autocontido — busca e salva no próprio
 * endpoint (`/api/admin/settings/booking-limit`) — para poder morar em qualquer aba de
 * Configurações sem depender do formulário do bot.
 */
export function BookingLimitCard({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error, reload } = useApi<BookingLimit>(
    () => api.get("/api/admin/settings/booking-limit"),
    [reloadKey],
  );
  const toast = useToast();
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setValue(data?.maxFutureBookingsPerCustomer != null ? String(data.maxFutureBookingsPerCustomer) : "");
  }, [data]);

  const save = async () => {
    const vazio = value.trim() === "";
    const parsed = Number(value);
    if (!vazio && (!Number.isInteger(parsed) || parsed < 1 || parsed > 50)) {
      toast.error("Informe um número inteiro de 1 a 50 — ou deixe vazio para não limitar.");
      return;
    }
    setSaving(true);
    try {
      await api.put("/api/admin/settings/booking-limit", { maxFutureBookingsPerCustomer: vazio ? null : parsed });
      toast.success(vazio ? "Limite removido." : "Limite salvo ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar o limite.");
    } finally {
      setSaving(false);
    }
  };

  const atual = data?.maxFutureBookingsPerCustomer;

  return (
    <div className="card">
      <div className="card-head">
        <h3>Limite de agendamentos por cliente</h3>
        {!loading && !error && (
          <span className={`badge ${atual != null ? "badge-green" : "badge-muted"}`}>
            {atual != null ? `${atual} por cliente` : "sem limite"}
          </span>
        )}
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Quantos horários <strong>futuros</strong> um mesmo cliente pode ter ao mesmo tempo. Evita
          quem reserva vários horários "para garantir" e trava a agenda. No limite, o bot responde
          com gentileza, lista os horários que ele já tem e oferece remarcar. Deixe vazio para não
          limitar.
        </p>
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : (
          <div className="stack">
            <div className="field">
              <label>Horários futuros por cliente
                <input
                  className="input"
                  inputMode="numeric"
                  value={value}
                  placeholder="sem limite"
                  onChange={(e) => setValue(e.target.value.replace(/\D/g, ""))}
                  style={{ maxWidth: 160 }}
                />
              </label>
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar limite"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
