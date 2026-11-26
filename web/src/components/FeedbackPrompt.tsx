// Feedback obrigatório do teste grátis (V57, piloto de 26/11). Aparece para o dono no 3º dia, no
// 7º e no fim do teste. Dá para adiar cada etapa UMA vez, por 24 h; depois disso a janela não
// fecha até a resposta (o painel fica atrás dela).
import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { Modal } from "./Modal";

type Pending = { stage: "D3" | "D7" | "FIM"; blocking: boolean; canSnooze: boolean };

const TITLE: Record<Pending["stage"], string> = {
  D3: "Como estão os primeiros dias com o Razor?",
  D7: "Uma semana de Razor: o que está achando?",
  FIM: "Fim do teste: sua opinião vale ouro",
};

export function FeedbackPrompt() {
  const [pending, setPending] = useState<Pending | null>(null);
  const [score, setScore] = useState<number | null>(null);
  const [overall, setOverall] = useState("");
  const [liked, setLiked] = useState("");
  const [missing, setMissing] = useState("");
  const [wouldPay, setWouldPay] = useState<"" | "SIM" | "TALVEZ" | "NAO">("");
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Pending>("/api/admin/trial-feedback/pending")
      .then((p) => setPending(p && p.stage ? p : null))
      .catch(() => setPending(null));
  }, []);

  if (!pending) return null;
  const fim = pending.stage === "FIM";

  const snooze = async () => {
    if (!pending.canSnooze) return;
    try {
      await api.post("/api/admin/trial-feedback/snooze", { stage: pending.stage });
    } catch { /* se falhar, só fecha agora e volta na próxima abertura */ }
    setPending(null);
  };

  const send = async () => {
    setError(null);
    if (score === null) return setError("Escolha uma nota de 0 a 10.");
    if (!overall.trim()) return setError("Conte em poucas palavras o que está achando.");
    if (fim && !wouldPay) return setError("Responda se pagaria para continuar.");
    setBusy(true);
    try {
      await api.post("/api/admin/trial-feedback", {
        stage: pending.stage, score, overall, liked, missing,
        wouldPay: fim ? wouldPay : null, priceSuggestion: fim ? price : null,
      });
      setPending(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível enviar. Tente de novo.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={TITLE[pending.stage]}
      onClose={() => setPending(null)}
      onRequestClose={() => { if (pending.canSnooze) void snooze(); return false; }}
      footer={
        <>
          {pending.canSnooze && <button className="btn btn-ghost" onClick={snooze} disabled={busy}>Responder depois (até amanhã)</button>}
          <button className="btn btn-primary" onClick={send} disabled={busy}>{busy ? "Enviando…" : "Enviar feedback"}</button>
        </>
      }
    >
      <div className="stack fb-form" style={{ gap: 16 }}>
        <p className="muted" style={{ margin: 0 }}>
          O teste é grátis e, em troca, a sua opinião ajuda a deixar o Razor do jeito que a barbearia precisa.
          Leva 1 minuto.{pending.blocking && <b> Responda para continuar usando o painel.</b>}
        </p>
        <div>
          <div className="fb-label">De 0 a 10, quanto você indicaria o Razor para outro barbeiro?</div>
          <div className="fb-scale" role="radiogroup" aria-label="Nota de 0 a 10">
            {Array.from({ length: 11 }, (_, n) => (
              <button key={n} type="button" role="radio" aria-checked={score === n}
                className={`fb-n${score === n ? " on" : ""}`} onClick={() => setScore(n)}>{n}</button>
            ))}
          </div>
        </div>
        <label className="field">
          <span className="fb-label">O que está achando do sistema?</span>
          <textarea className="input" rows={3} value={overall} onChange={(e) => setOverall(e.target.value)} maxLength={2000} />
        </label>
        <label className="field">
          <span className="fb-label">O que mais ajudou até agora?</span>
          <textarea className="input" rows={2} value={liked} onChange={(e) => setLiked(e.target.value)} maxLength={2000} />
        </label>
        <label className="field">
          <span className="fb-label">O que falta ou atrapalhou?</span>
          <textarea className="input" rows={2} value={missing} onChange={(e) => setMissing(e.target.value)} maxLength={2000} />
        </label>
        {fim && (
          <>
            <div>
              <div className="fb-label">Você pagaria para continuar usando o Razor?</div>
              <div className="fb-choices">
                {([["SIM", "Sim"], ["TALVEZ", "Talvez"], ["NAO", "Não"]] as const).map(([v, l]) => (
                  <button key={v} type="button" className={`btn ${wouldPay === v ? "btn-primary" : "btn-ghost"}`} onClick={() => setWouldPay(v)}>{l}</button>
                ))}
              </div>
            </div>
            <label className="field">
              <span className="fb-label">Quanto pagaria por mês, que fosse justo?</span>
              <input className="input" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="Ex.: R$ 79" maxLength={120} />
            </label>
          </>
        )}
        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </Modal>
  );
}
