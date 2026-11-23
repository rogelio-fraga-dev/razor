import { useEffect, useState } from "react";
import { SkeletonRows } from "./Skeleton";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { ReviewSettings } from "../api/types";

/** Mesma regra do backend (GoogleReviewUrlPolicy): https em domínio do Google, g.page ou maps.app.goo.gl. */
export function isGoogleReviewUrl(raw: string): boolean {
  let u: URL;
  try {
    u = new URL(raw.trim());
  } catch {
    return false;
  }
  if (u.protocol !== "https:" || u.username || u.password || (u.port && u.port !== "443")) return false;
  const h = u.hostname.toLowerCase();
  return h === "g.page" || h === "maps.app.goo.gl" || /^([a-z0-9-]+\.)*google\.com(\.br)?$/.test(h);
}

/**
 * Avaliação pós-atendimento (V46): depois que a comanda fecha, o bot pede uma nota de 1 a 5.
 * Nota 4–5 recebe o link do Google; nota baixa vai só para você. Card autocontido, como o do
 * anti-falta — busca e salva em `/api/admin/settings/reviews`.
 */
export function ReviewSettingsCard({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error, reload } = useApi<ReviewSettings>(
    () => api.get("/api/admin/settings/reviews"),
    [reloadKey],
  );
  const toast = useToast();
  const [enabled, setEnabled] = useState(true);
  const [url, setUrl] = useState("");
  const [delay, setDelay] = useState("120");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!data) return;
    setEnabled(data.enabled);
    setUrl(data.googleReviewUrl ?? "");
    setDelay(String(data.delayMinutes));
  }, [data]);

  const urlInvalid = url.trim() !== "" && !isGoogleReviewUrl(url);

  const save = async () => {
    const d = Number(delay);
    if (!Number.isInteger(d) || d < 15 || d > 1440) {
      toast.error("Tempo depois do atendimento: de 15 a 1440 minutos.");
      return;
    }
    if (urlInvalid) {
      toast.error("O link precisa ser do Google (g.page, search.google.com ou maps.app.goo.gl).");
      return;
    }
    setSaving(true);
    try {
      await api.put("/api/admin/settings/reviews", {
        enabled,
        googleReviewUrl: url.trim() === "" ? null : url.trim(),
        delayMinutes: d,
      });
      toast.success("Avaliação salva ✓");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h3>Avaliação depois do atendimento</h3>
        {!loading && !error && (
          <span className={`badge ${enabled ? "badge-green" : "badge-muted"}`}>{enabled ? "ligada" : "desligada"}</span>
        )}
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Quando a comanda fecha, o bot pergunta: <em>“de 1 a 5, que nota você dá pro seu atendimento?”</em>.
          Quem dá <strong>4 ou 5</strong> recebe o link do Google; nota <strong>1 a 3</strong> nunca vai pro
          Google — você recebe um alerta no WhatsApp com o que o cliente contou. No máximo um pedido por
          cliente a cada 30 dias, só no horário de funcionamento, e quem responder “parar” não é mais perguntado.
        </p>
        {loading ? (
          <SkeletonRows rows={2} />
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : (
          <div className="stack">
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
              Pedir nota de 1 a 5 depois do atendimento
            </label>
            <div className="field">
              <label>
                Link de avaliação do Google
                <input
                  className="input"
                  value={url}
                  disabled={!enabled}
                  placeholder="https://g.page/r/…/review"
                  onChange={(e) => setUrl(e.target.value)}
                  aria-invalid={urlInvalid || undefined}
                />
              </label>
            </div>
            {urlInvalid && (
              <div className="error" role="alert" style={{ fontSize: 13 }}>
                Use o link do seu Perfil da Empresa no Google (g.page, search.google.com ou maps.app.goo.gl).
              </div>
            )}
            <p className="muted" style={{ fontSize: 13, margin: 0 }}>
              No Perfil da Empresa no Google, toque em “Pedir avaliações” e copie o link. Sem link, o bot só agradece.
            </p>
            <div className="field">
              <label>
                Minutos depois do atendimento
                <input
                  className="input"
                  inputMode="numeric"
                  value={delay}
                  disabled={!enabled}
                  onChange={(e) => setDelay(e.target.value.replace(/\D/g, ""))}
                  style={{ maxWidth: 140 }}
                />
              </label>
            </div>
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar avaliação"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
