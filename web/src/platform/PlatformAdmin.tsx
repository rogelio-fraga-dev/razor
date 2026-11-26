// Console com controle total (26/11): barbearias e ações, feedback do teste grátis e o relatório
// organizado para mandar ao Claude. Toda ação exige o 2FA ativo e fica na Trilha.
import { useState } from "react";
import { ApiError } from "../api/client";
import { useApi } from "../lib/useApi";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { papi, dt } from "./api";

type ShopAdmin = {
  id: string; name: string; slug: string; city: string | null; status: string; plan: string | null;
  trial_ends_at: string | null; created_at: string; note: string | null; whatsapp_instance: string | null;
  owner_name: string | null; owner_email: string | null; customers: number; appts_7d: number; bot_appts_7d: number;
  comandas_7d: number; active_days_7d: number; last_message_at: string | null; feedbacks: number;
};
type FeedbackRow = {
  stage: string; score: number; overall: string | null; liked: string | null; missing: string | null;
  would_pay: string | null; price_suggestion: string | null; created_at: string; barbershop: string;
};

const STATUS: Record<string, string> = { TRIAL: "teste", ACTIVE: "ativa", SUSPENDED: "suspensa" };
const STAGE: Record<string, string> = { D3: "3º dia", D7: "7º dia", FIM: "fim do teste" };

function Loading({ loading, error }: { loading: boolean; error: string | null }) {
  if (error) return <div className="error" role="alert">{error}</div>;
  return loading ? <SkeletonRows rows={4} /> : null;
}

const err = (e: unknown) => (e instanceof ApiError ? e.message : "Não foi possível concluir.");

// ------------------------------------------------------------------ barbearias
export function ShopsAdminTab() {
  const { data, loading, error, reload } = useApi<ShopAdmin[]>(() => papi.get("/shops-admin"), []);
  const toast = useToast();
  const [open, setOpen] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (label: string, fn: () => Promise<unknown>) => {
    setBusy(true);
    try { await fn(); toast.success(label); reload(); } catch (e) { toast.error(err(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <h2 style={{ margin: 0 }}>Barbearias</h2>
      <p className="muted" style={{ margin: 0 }}>Uso dos últimos 7 dias e as ações de suporte. Tudo o que você faz aqui entra na Trilha.</p>
      {!data ? <Loading loading={loading} error={error} /> : data.length === 0 ? <p className="muted">Nenhuma barbearia ainda.</p> : (
        <div className="stack" style={{ gap: 12 }}>
          {data.map((s) => (
            <div className="card" key={s.id}>
              <div className="card-pad shop-admin">
                <button className="shop-admin-head" onClick={() => setOpen(open === s.id ? null : s.id)} aria-expanded={open === s.id}>
                  <div>
                    <b style={{ fontSize: 16 }}>{s.name}</b>
                    <div className="muted" style={{ fontSize: 13 }}>{s.owner_name ?? "—"} · {s.owner_email ?? "—"} · {s.city ?? "—"}</div>
                  </div>
                  <div className="shop-admin-badges">
                    <span className={`badge ${s.status === "SUSPENDED" ? "badge-amber" : s.status === "TRIAL" ? "badge-blue" : "badge-green"}`}>{STATUS[s.status] ?? s.status}</span>
                    <span className="badge badge-muted">{s.plan ?? "sem plano"}</span>
                  </div>
                </button>
                <div className="shop-admin-kpis">
                  <span><b>{s.active_days_7d}</b>/7 dias com uso</span>
                  <span><b>{s.bot_appts_7d}</b> de {s.appts_7d} horários pela IA</span>
                  <span><b>{s.comandas_7d}</b> comandas</span>
                  <span><b>{s.customers}</b> clientes</span>
                  <span><b>{s.feedbacks}</b> feedbacks</span>
                  <span>teste até <b>{s.trial_ends_at ? dt(s.trial_ends_at) : "—"}</b></span>
                </div>
                {open === s.id && (
                  <div className="shop-admin-actions">
                    <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
                      <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => run("Teste estendido em 7 dias.", () => papi.post(`/shops/${s.id}/extend-trial`, { days: 7 }))}>+7 dias de teste</button>
                      <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => run("Teste estendido em 14 dias.", () => papi.post(`/shops/${s.id}/extend-trial`, { days: 14 }))}>+14 dias</button>
                      {s.status !== "ACTIVE" && <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => run("Marcada como ativa (cliente pagante).", () => papi.post(`/shops/${s.id}/status`, { value: "ACTIVE" }))}>Marcar como ativa</button>}
                      {s.status !== "SUSPENDED" ? (
                        <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => {
                          if (window.confirm(`Suspender ${s.name}? O painel deles sai do ar e o bot para de responder.`))
                            void run("Barbearia suspensa.", () => papi.post(`/shops/${s.id}/status`, { value: "SUSPENDED" }));
                        }}>Suspender</button>
                      ) : (
                        <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => run("Barbearia reativada.", () => papi.post(`/shops/${s.id}/status`, { value: "TRIAL" }))}>Reativar</button>
                      )}
                      <select className="input" style={{ width: "auto", minHeight: 32 }} value={s.plan ?? ""} disabled={busy}
                        onChange={(e) => run("Plano alterado.", () => papi.post(`/shops/${s.id}/plan`, { value: e.target.value }))}>
                        <option value="">sem plano (tudo liberado)</option>
                        <option value="PILOTO">Piloto</option>
                        <option value="SOLO">Solo</option>
                        <option value="EQUIPE">Equipe</option>
                        <option value="PRO">Pro</option>
                      </select>
                      <button className="btn btn-ghost btn-sm" disabled={busy} onClick={async () => {
                        if (!window.confirm(`Gerar uma senha nova para o dono de ${s.name}? As sessões abertas caem.`)) return;
                        try {
                          const r = await papi.post<{ password: string }>(`/shops/${s.id}/reset-owner-password`);
                          window.prompt("Senha provisória do dono (copie agora, ela não aparece de novo):", r.password);
                        } catch (e) { toast.error(err(e)); }
                      }}>Redefinir senha do dono</button>
                    </div>
                    <NoteEditor shop={s} onSaved={reload} />
                    <p className="faint" style={{ fontSize: 12.5, margin: 0 }}>
                      Endereço público: /b/{s.slug} · última mensagem de cliente: {s.last_message_at ? dt(s.last_message_at) : "nenhuma"} · criada em {dt(s.created_at)}
                    </p>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

function NoteEditor({ shop, onSaved }: { shop: ShopAdmin; onSaved: () => void }) {
  const [note, setNote] = useState(shop.note ?? "");
  const toast = useToast();
  return (
    <div className="stack" style={{ gap: 6 }}>
      <label className="muted" style={{ fontSize: 13 }} htmlFor={`note-${shop.id}`}>Anotação interna (só você vê)</label>
      <textarea id={`note-${shop.id}`} className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} maxLength={2000} />
      <div><button className="btn btn-ghost btn-sm" onClick={async () => {
        try { await papi.post(`/shops/${shop.id}/note`, { value: note }); toast.success("Anotação salva."); onSaved(); } catch (e) { toast.error(err(e)); }
      }}>Salvar anotação</button></div>
    </div>
  );
}

// ------------------------------------------------------------------ feedback
export function FeedbackTab() {
  const { data, loading, error } = useApi<{ answers: FeedbackRow[]; overdue: { barbershop: string; stage: string }[] }>(() => papi.get("/feedback"), []);
  const answers = data?.answers ?? [];
  const avg = answers.length ? (answers.reduce((a, b) => a + b.score, 0) / answers.length).toFixed(1) : "—";
  const pay = answers.filter((a) => a.stage === "FIM");
  return (
    <>
      <h2 style={{ margin: 0 }}>Feedback do teste grátis</h2>
      <p className="muted" style={{ margin: 0 }}>Respostas do 3º dia, do 7º e do fim do teste.</p>
      {!data ? <Loading loading={loading} error={error} /> : (
        <>
          <div className="shop-admin-kpis">
            <span><b>{answers.length}</b> respostas</span>
            <span>nota média <b>{avg}</b>/10</span>
            <span><b>{pay.filter((a) => a.would_pay === "SIM").length}</b> de {pay.length} pagariam</span>
          </div>
          {data.overdue.length > 0 && (
            <div className="card"><div className="card-pad">
              <b>Ainda sem responder:</b> {data.overdue.map((o) => `${o.barbershop} (${STAGE[o.stage] ?? o.stage})`).join(" · ")}
            </div></div>
          )}
          {answers.length === 0 ? <p className="muted">Nenhuma resposta ainda.</p> : answers.map((a, i) => (
            <div className="card" key={i}><div className="card-pad stack" style={{ gap: 6 }}>
              <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
                <b>{a.barbershop} · {STAGE[a.stage] ?? a.stage}</b>
                <span className="badge badge-plan">nota {a.score}/10</span>
              </div>
              {a.overall && <p style={{ margin: 0 }}><b>Achando:</b> {a.overall}</p>}
              {a.liked && <p style={{ margin: 0 }}><b>Ajudou:</b> {a.liked}</p>}
              {a.missing && <p style={{ margin: 0 }}><b>Falta:</b> {a.missing}</p>}
              {a.would_pay && <p style={{ margin: 0 }}><b>Pagaria:</b> {a.would_pay}{a.price_suggestion ? ` · ${a.price_suggestion}` : ""}</p>}
              <span className="faint" style={{ fontSize: 12.5 }}>{dt(a.created_at)}</span>
            </div></div>
          ))}
        </>
      )}
    </>
  );
}

// ------------------------------------------------------------------ relatório
export function ReportTab() {
  const [days, setDays] = useState(7);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const load = async (download: boolean) => {
    setBusy(true);
    try {
      const r = await fetch(`/api/platform/report?days=${days}`, { credentials: "include" });
      if (!r.ok) throw new Error(r.status === 403 ? "Ative o 2FA para gerar o relatório." : "Não foi possível gerar o relatório.");
      const text = await r.text();
      setPreview(text);
      if (download) {
        const url = URL.createObjectURL(new Blob([text], { type: "text/markdown;charset=utf-8" }));
        const a = document.createElement("a");
        a.href = url; a.download = `razor-relatorio-${new Date().toISOString().slice(0, 10)}.md`; a.click();
        URL.revokeObjectURL(url);
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao gerar.");
    } finally { setBusy(false); }
  };
  return (
    <>
      <h2 style={{ margin: 0 }}>Relatório para o Claude</h2>
      <p className="muted" style={{ margin: 0 }}>
        Junta, organizado: uso de cada barbearia, erros do sistema agrupados, agendamentos que falharam, incidentes,
        consumo e custo da IA, feedbacks e as suas ações no console. Telefones e e-mails de clientes saem mascarados.
        Baixe o arquivo e mande na conversa.
      </p>
      <div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
        <select className="input" style={{ width: "auto" }} value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={1}>último dia</option><option value={3}>últimos 3 dias</option><option value={7}>últimos 7 dias</option><option value={14}>últimos 14 dias</option>
        </select>
        <button className="btn btn-primary" disabled={busy} onClick={() => load(true)}>{busy ? "Gerando…" : "Baixar relatório (.md)"}</button>
        <button className="btn btn-ghost" disabled={busy} onClick={() => load(false)}>Ver aqui</button>
      </div>
      {preview && <pre className="card report-preview">{preview}</pre>}
    </>
  );
}
