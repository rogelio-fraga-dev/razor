import { useEffect, useState } from "react";
import { useToast } from "./Toast";
import { useApi } from "../lib/useApi";
import { api, ApiError } from "../api/client";
import type { NoShowSettings } from "../api/types";

/**
 * Anti-falta (V42): confirmação da véspera, liberar a vaga de quem não responde e sinal para quem
 * falta muito. Card autocontido — busca e salva em `/api/admin/settings/no-show` — como o
 * BookingLimitCard, para não depender do formulário do bot.
 */
export function NoShowSettingsCard({ reloadKey = 0 }: { reloadKey?: number }) {
  const { data, loading, error, reload } = useApi<NoShowSettings>(
    () => api.get("/api/admin/settings/no-show"),
    [reloadKey],
  );
  const toast = useToast();
  const [enabled, setEnabled] = useState(true);
  const [hour, setHour] = useState("18");
  const [deadline, setDeadline] = useState("3");
  const [autoRelease, setAutoRelease] = useState(false);
  const [depositAfter, setDepositAfter] = useState("");
  const [depositAmount, setDepositAmount] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!data) return;
    setEnabled(data.confirmationEnabled);
    setHour(String(data.confirmationHour));
    setDeadline(String(data.confirmationDeadlineHours));
    setAutoRelease(data.autoReleaseUnconfirmed);
    setDepositAfter(data.noShowDepositAfter != null ? String(data.noShowDepositAfter) : "");
    setDepositAmount(data.noShowDepositAmount != null ? String(data.noShowDepositAmount).replace(".", ",") : "");
  }, [data]);

  const save = async () => {
    const h = Number(hour);
    const dl = Number(deadline);
    if (!Number.isInteger(h) || h < 0 || h > 23) {
      toast.error("Hora da pergunta: de 0 a 23.");
      return;
    }
    if (!Number.isInteger(dl) || dl < 1 || dl > 23) {
      toast.error("Prazo para responder: de 1 a 23 horas antes.");
      return;
    }
    const after = depositAfter.trim() === "" ? null : Number(depositAfter);
    if (after != null && (!Number.isInteger(after) || after < 1 || after > 20)) {
      toast.error("Faltas para exigir sinal: de 1 a 20 — ou deixe vazio para desligar.");
      return;
    }
    const amount = depositAmount.trim() === "" ? null : Number(depositAmount.replace(/\./g, "").replace(",", "."));
    if (amount != null && (!Number.isFinite(amount) || amount <= 0)) {
      toast.error("Valor do sinal inválido — ou deixe vazio para usar o preço do serviço.");
      return;
    }
    setSaving(true);
    try {
      await api.put("/api/admin/settings/no-show", {
        confirmationEnabled: enabled,
        confirmationHour: h,
        confirmationDeadlineHours: dl,
        autoReleaseUnconfirmed: autoRelease,
        noShowDepositAfter: after,
        noShowDepositAmount: amount,
      });
      toast.success("Anti-falta salvo ✓");
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
        <h3>Anti-falta: confirmação e sinal</h3>
        {!loading && !error && (
          <span className={`badge ${enabled ? "badge-green" : "badge-muted"}`}>
            {enabled ? "confirmação ligada" : "desligado"}
          </span>
        )}
      </div>
      <div className="card-pad">
        <p className="muted" style={{ marginTop: 0, fontSize: 14 }}>
          Na véspera, o bot pergunta ao cliente: <em>“responde 1 pra confirmar ou 2 pra desmarcar”</em>.
          Quem desmarca libera a vaga na hora (e a lista de espera é avisada). Horários marcados depois
          da hora da pergunta não são perguntados — o lembrete de 1h antes continua saindo.
        </p>
        {loading ? (
          <div className="muted">Carregando…</div>
        ) : error ? (
          <div className="error" role="alert">{error}</div>
        ) : (
          <div className="stack">
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
              Perguntar na véspera se o cliente confirma
            </label>
            <div className="row wrap" style={{ gap: 12 }}>
              <div className="field">
                <label>Hora da pergunta (véspera)
                  <input className="input" inputMode="numeric" value={hour} disabled={!enabled}
                    onChange={(e) => setHour(e.target.value.replace(/\D/g, ""))} style={{ maxWidth: 120 }} />
                </label>
              </div>
              <div className="field">
                <label>Prazo (horas antes)
                  <input className="input" inputMode="numeric" value={deadline} disabled={!enabled}
                    onChange={(e) => setDeadline(e.target.value.replace(/\D/g, ""))} style={{ maxWidth: 120 }} />
                </label>
              </div>
            </div>
            <p className="muted" style={{ fontSize: 13, margin: 0 }}>
              Sem resposta até o prazo, você recebe um aviso no WhatsApp (“Fulano não confirmou o horário das 15h”).
            </p>
            <label className="row" style={{ gap: 8 }}>
              <input type="checkbox" checked={autoRelease} disabled={!enabled}
                onChange={(e) => setAutoRelease(e.target.checked)} />
              Sem resposta no prazo, liberar o horário para a lista de espera
            </label>

            <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "4px 0" }} />

            <div className="row wrap" style={{ gap: 12 }}>
              <div className="field">
                <label>Exigir sinal a partir de (faltas em 180 dias)
                  <input className="input" inputMode="numeric" value={depositAfter} placeholder="desligado"
                    onChange={(e) => setDepositAfter(e.target.value.replace(/\D/g, ""))} style={{ maxWidth: 140 }} />
                </label>
              </div>
              <div className="field">
                <label>Valor do sinal (R$)
                  <input className="input" inputMode="decimal" value={depositAmount} placeholder="preço do serviço"
                    onChange={(e) => setDepositAmount(e.target.value.replace(/[^\d,.]/g, ""))} style={{ maxWidth: 160 }} />
                </label>
              </div>
            </div>
            <p className="muted" style={{ fontSize: 13, margin: 0 }}>
              Quem marcar pelo bot com esse número de faltas recebe a sua chave PIX e o horário fica com
              “sinal pendente” na agenda — você confirma o recebimento lá. O sistema não confere o PIX
              sozinho nem cancela se o sinal não vier.
            </p>
            {data?.pixConfigured === false && depositAfter.trim() !== "" && (
              <div className="error" role="alert" style={{ fontSize: 13 }}>
                Sem chave PIX configurada (aba Bot & IA), a regra do sinal não vale.
              </div>
            )}
            <div className="row">
              <button className="btn btn-primary" onClick={save} disabled={saving}>
                {saving ? "Salvando…" : "Salvar anti-falta"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
