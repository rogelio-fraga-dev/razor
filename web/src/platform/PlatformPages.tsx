import { useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { useApi } from "../lib/useApi";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { papi, dt, brl, usd, type AiView, type AuditEntry, type IncidentRow, type Overview } from "./api";

const STATUS: Record<string, string> = { TRIAL: "teste", ACTIVE: "ativa", INATIVA: "inativa" };

function Loading({ loading, error }: { loading: boolean; error: string | null }) {
  if (error) return <div className="error" role="alert">{error}</div>;
  return loading ? <SkeletonRows rows={4} /> : null;
}

// ------------------------------------------------------------------ visão geral

export function OverviewTab() {
  const [days, setDays] = useState(30);
  const { data, loading, error } = useApi<Overview>(() => papi.get(`/overview?days=${days}`), [days]);

  return (
    <>
      <div className="plat-toolbar">
        <h2 style={{ margin: 0, flex: 1 }}>Visão geral</h2>
        <select className="input" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Período">
          <option value={7}>7 dias</option>
          <option value={30}>30 dias</option>
          <option value={90}>90 dias</option>
        </select>
      </div>
      {!data ? <Loading loading={loading} error={error} /> : (
        <>
          <div className="plat-kpis">
            <div className="plat-kpi"><div className="l">Barbearias</div><div className="v">{data.shops}</div>
              <div className="l">{Object.entries(data.byStatus).map(([k, v]) => `${v} ${STATUS[k] ?? k}`).join(" · ")}</div></div>
            <div className="plat-kpi"><div className="l">Mensagens ({data.days}d)</div><div className="v">{data.messages}</div></div>
            <div className="plat-kpi"><div className="l">Agendamentos da IA ({data.days}d)</div><div className="v">{data.botAppointments}</div></div>
            <div className="plat-kpi"><div className="l">Chamadas à IA ({data.days}d)</div><div className="v">{data.aiCalls}</div></div>
            <div className="plat-kpi"><div className="l">Custo estimado da IA</div><div className="v">{brl(data.aiCostBrl)}</div>
              <div className="l">{usd(data.aiCostUsd)} · US$ 1 = {brl(data.usdToBrl)}</div></div>
          </div>
          <div className="card">
            <div className="card-pad" style={{ overflowX: "auto" }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Barbearia</th><th>Status</th><th>Clientes</th><th>Agend.</th><th>Pela IA</th>
                    <th>Mensagens</th><th>IA (chamadas/falhas)</th><th>Custo IA</th><th>Alertas</th>
                  </tr>
                </thead>
                <tbody>
                  {data.rows.map((r) => (
                    <tr key={r.id}>
                      <td>
                        <b>{r.name}</b>
                        <div className="muted" style={{ fontSize: 12 }}>
                          {r.slug}{r.city ? ` · ${r.city}` : ""}{r.plan ? ` · plano ${r.plan}` : ""}
                          {!r.whatsappConfigured ? " · sem WhatsApp" : ""}
                        </div>
                      </td>
                      <td>
                        <span className={`badge ${!r.active ? "badge-muted" : r.status === "TRIAL" ? "badge-amber" : "badge-green"}`}>
                          {!r.active ? "inativa" : STATUS[r.status] ?? r.status}
                        </span>
                        {r.status === "TRIAL" && r.trialEndsAt && <div className="muted" style={{ fontSize: 12 }}>até {dt(r.trialEndsAt)}</div>}
                      </td>
                      <td>{r.customers}</td>
                      <td>{r.appointments}</td>
                      <td>{r.botAppointments}</td>
                      <td>{r.messages}</td>
                      <td>{r.aiCalls}{r.aiFailures > 0 && <span style={{ color: "var(--red)" }}> / {r.aiFailures}</span>}</td>
                      <td title={usd(r.aiCostUsd)}>{brl(r.aiCostBrl)}</td>
                      <td>{r.openIncidents > 0 ? <span className="badge badge-amber">{r.openIncidents}</span> : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted" style={{ fontSize: 12.5 }}>
                Custo estimado pelos tokens registrados e pela tabela de preços configurada — confira a fatura real na OpenAI.
              </p>
            </div>
          </div>
        </>
      )}
    </>
  );
}

// ------------------------------------------------------------------ saúde

export function HealthTab() {
  const { data, loading, error, reload } = useApi<IncidentRow[]>(() => papi.get("/incidents?days=30"), []);
  const shops = useApi<Overview>(() => papi.get("/overview?days=7"), []);
  const toast = useToast();
  const [wpp, setWpp] = useState<Record<string, string>>({});
  const [qr, setQr] = useState<{ shop: string; data: Record<string, unknown> } | null>(null);

  const checkState = async (id: string) => {
    try {
      const s = await papi.get<{ state: string; connected: boolean }>(`/shops/${id}/whatsapp`);
      setWpp((w) => ({ ...w, [id]: s.connected ? "conectado" : s.state }));
    } catch (e) {
      setWpp((w) => ({ ...w, [id]: e instanceof ApiError ? e.message : "erro" }));
    }
  };

  const connect = async (id: string, name: string) => {
    if (!window.confirm(`Gerar um novo QR de pareamento para "${name}"? O dono precisa ler no celular dele.`)) return;
    try {
      setQr({ shop: name, data: await papi.post<Record<string, unknown>>(`/shops/${id}/whatsapp/connect`) });
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Não consegui gerar o QR.");
    }
  };

  const open = data?.filter((i) => i.status === "OPEN") ?? [];
  const closed = data?.filter((i) => i.status !== "OPEN") ?? [];
  const qrImage = qr && typeof qr.data.qrCodeBase64 === "string" ? qr.data.qrCodeBase64 : null;
  const pairing = qr && typeof qr.data.pairingCode === "string" ? qr.data.pairingCode : null;

  return (
    <>
      <div className="plat-toolbar">
        <h2 style={{ margin: 0, flex: 1 }}>Saúde</h2>
        <button className="btn btn-ghost btn-sm" onClick={reload}>Atualizar</button>
      </div>
      <div className="card">
        <div className="card-head"><h3>Alertas abertos</h3>
          <span className={`badge ${open.length ? "badge-amber" : "badge-green"}`}>{open.length || "nenhum"}</span></div>
        <div className="card-pad">
          {!data ? <Loading loading={loading} error={error} /> : open.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>Tudo funcionando em todas as barbearias.</p>
          ) : (
            <IncidentTable rows={open} />
          )}
        </div>
      </div>
      <div className="card">
        <div className="card-head"><h3>WhatsApp por barbearia</h3></div>
        <div className="card-pad" style={{ overflowX: "auto" }}>
          {!shops.data ? <Loading loading={shops.loading} error={shops.error} /> : (
            <table className="table">
              <thead><tr><th>Barbearia</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                {shops.data.rows.map((r) => (
                  <tr key={r.id}>
                    <td>{r.name}</td>
                    <td>{!r.whatsappConfigured ? <span className="muted">não configurado</span> : wpp[r.id] ?? "—"}</td>
                    <td className="row" style={{ gap: 6, justifyContent: "flex-end" }}>
                      <button className="btn btn-ghost btn-sm" onClick={() => checkState(r.id)}>Verificar</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => connect(r.id, r.name)}>Gerar QR</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {qr && (
            <div className="stack" style={{ marginTop: 12 }}>
              <b>Pareamento — {qr.shop}</b>
              {qrImage ? <img src={qrImage.startsWith("data:") ? qrImage : `data:image/png;base64,${qrImage}`} alt="QR do WhatsApp" style={{ width: 240 }} />
                : <span className="muted">A Evolution não devolveu QR (instância já conectada ou fora do ar).</span>}
              {pairing && <span>Código de pareamento: <code>{pairing}</code></span>}
              <button className="btn btn-ghost btn-sm" style={{ justifySelf: "start" }} onClick={() => setQr(null)}>Fechar</button>
            </div>
          )}
        </div>
      </div>
      <div className="card">
        <div className="card-head"><h3>Histórico (30 dias)</h3></div>
        <div className="card-pad">{data && (closed.length ? <IncidentTable rows={closed} /> : <p className="muted" style={{ margin: 0 }}>Nada resolvido no período.</p>)}</div>
      </div>
    </>
  );
}

function IncidentTable({ rows }: { rows: IncidentRow[] }) {
  return (
    <div style={{ overflowX: "auto" }}>
      <table className="table">
        <thead><tr><th>Onde</th><th>Problema</th><th>Desde</th><th>Última vez</th><th>Ocorr.</th><th>Resolvido</th></tr></thead>
        <tbody>
          {rows.map((i) => (
            <tr key={i.id}>
              <td>{i.barbershopName ?? <span className="badge badge-amber">plataforma</span>}</td>
              <td>{i.message}{i.detail && <div className="muted" style={{ fontSize: 12 }}>{i.detail}</div>}</td>
              <td>{dt(i.firstSeenAt)}</td>
              <td>{dt(i.lastSeenAt)}</td>
              <td>{i.occurrences}</td>
              <td>{dt(i.resolvedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ------------------------------------------------------------------ IA

export function AiTab() {
  const { data, loading, error, reload } = useApi<AiView>(() => papi.get("/ai"), []);
  const toast = useToast();
  const [key, setKey] = useState("");
  const [chat, setChat] = useState("");
  const [esc, setEsc] = useState("");
  const [tr, setTr] = useState("");
  const [busy, setBusy] = useState(false);
  const [test, setTest] = useState<{ ok: boolean; message: string } | null>(null);

  useEffect(() => {
    if (!data) return;
    setChat(data.chatModel === data.envChatModel ? "" : data.chatModel);
    setEsc(data.escalationModel === data.envEscalationModel ? "" : data.escalationModel);
    setTr(data.transcribeModel === data.envTranscribeModel ? "" : data.transcribeModel);
  }, [data]);

  const save = async (clearKey = false) => {
    if (clearKey && !window.confirm("Voltar a usar a chave do ambiente (OPENAI_API_KEY)?")) return;
    setBusy(true);
    try {
      await papi.put("/ai", { apiKey: clearKey ? null : key || null, clearKey, chatModel: chat, escalationModel: esc, transcribeModel: tr });
      setKey("");
      setTest(null);
      toast.success("Configuração da IA salva — já vale para as próximas mensagens.");
      reload();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Erro ao salvar.");
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setBusy(true);
    try {
      setTest(await papi.post("/ai/test", { apiKey: key || null }));
    } catch (e) {
      setTest({ ok: false, message: e instanceof ApiError ? e.message : "Falhou." });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <h2 style={{ margin: 0 }}>Inteligência artificial</h2>
      <div className="card">
        <div className="card-pad stack">
          {!data ? <Loading loading={loading} error={error} /> : (
            <>
              <p style={{ margin: 0 }}>
                Chave em uso: <b>{data.keyLast4 ? `…${data.keyLast4}` : "nenhuma"}</b>{" "}
                <span className={`badge ${data.keySource === "CONSOLE" ? "badge-blue" : "badge-muted"}`}>
                  {data.keySource === "CONSOLE" ? "definida no console" : "do ambiente (.env)"}
                </span>
                {data.updatedAt && <span className="muted"> · alterada em {dt(data.updatedAt)}</span>}
              </p>
              <div className="field">
                <label>Nova chave da OpenAI (fica cifrada; nunca é exibida inteira)
                  <input className="input" type="password" autoComplete="off" placeholder="sk-…" value={key}
                    onChange={(e) => setKey(e.target.value.trim())} />
                </label>
              </div>
              <div className="row wrap" style={{ gap: 12 }}>
                <div className="field"><label>Modelo do dia a dia
                  <input className="input" placeholder={data.envChatModel} value={chat} onChange={(e) => setChat(e.target.value)} /></label></div>
                <div className="field"><label>Modelo forte (quando o principal falha / imagem)
                  <input className="input" placeholder={data.envEscalationModel} value={esc} onChange={(e) => setEsc(e.target.value)} /></label></div>
                <div className="field"><label>Transcrição de áudio
                  <input className="input" placeholder={data.envTranscribeModel} value={tr} onChange={(e) => setTr(e.target.value)} /></label></div>
              </div>
              <p className="muted" style={{ margin: 0, fontSize: 13 }}>Campo de modelo vazio = o do ambiente (mostrado em cinza).</p>
              {test && <div className={test.ok ? "badge badge-green" : "error"} role="status">{test.message}</div>}
              <div className="row wrap" style={{ gap: 8 }}>
                <button className="btn btn-ghost" onClick={runTest} disabled={busy}>{key ? "Testar a chave nova" : "Testar a chave em uso"}</button>
                <button className="btn btn-primary" onClick={() => save(false)} disabled={busy}>Salvar</button>
                {data.keySource === "CONSOLE" && (
                  <button className="btn btn-danger" onClick={() => save(true)} disabled={busy}>Usar a chave do ambiente</button>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

// ------------------------------------------------------------------ trilha

export function AuditTab() {
  const { data, loading, error } = useApi<AuditEntry[]>(() => papi.get("/audit?limit=200"), []);
  const LABEL: Record<string, string> = {
    LOGIN: "entrou no console", READ_CONVERSATION: "leu uma conversa", AI_SETTINGS: "mudou a IA",
    WHATSAPP_CONNECT: "gerou QR do WhatsApp", MFA_ENABLED: "ativou o 2FA", MFA_DISABLED: "desativou o 2FA",
    RECOVERY_CODES: "gerou códigos de recuperação", LOGOUT_ALL: "saiu de todos os aparelhos",
    DEVICE_REVOKED: "esqueceu um aparelho",
  };
  const METHOD: Record<string, string> = {
    PASSWORD: "só senha", TOTP: "senha + app", RECOVERY: "código de recuperação", TRUSTED_DEVICE: "aparelho lembrado",
  };
  return (
    <>
      <h2 style={{ margin: 0 }}>Trilha de acesso</h2>
      <p className="muted" style={{ margin: 0 }}>Quem usou o console e leu dados de clientes (LGPD).</p>
      <div className="card"><div className="card-pad" style={{ overflowX: "auto" }}>
        {!data ? <Loading loading={loading} error={error} /> : (
          <table className="table">
            <thead><tr><th>Quando</th><th>Quem</th><th>O quê</th><th>Barbearia</th><th>Detalhe</th><th>IP</th></tr></thead>
            <tbody>
              {data.map((e) => (
                <tr key={e.id}>
                  <td>{dt(e.createdAt)}</td><td>{e.userEmail ?? "—"}</td><td>{LABEL[e.action] ?? e.action}</td>
                  <td>{e.barbershopName ?? "—"}</td><td>{e.action === "LOGIN" ? METHOD[e.target ?? ""] ?? e.target : e.target ?? "—"}</td><td>{e.ip ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div></div>
    </>
  );
}
