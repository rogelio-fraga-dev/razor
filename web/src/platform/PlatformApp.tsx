// Console da plataforma (V51/V56) — acesso do dono do Razor, separado do painel das barbearias:
// sessão própria (cookie só em /api/platform), 2FA com carência e sessão que expira parada.
// Carregado sob demanda: quem usa o painel da barbearia nunca baixa este código.
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { papi, lastActivity, SESSION_LOST, MFA_REQUIRED, type Me } from "./api";
import { OverviewTab, HealthTab, AiTab, AuditTab } from "./PlatformPages";
import { ConversationsTab, MetricsTab, RevenueTab } from "./PlatformData";
import { SecurityTab } from "./SecurityTab";
import { ShopsAdminTab, FeedbackTab, ReportTab } from "./PlatformAdmin";
import { CodeInput, MfaSetup, RecoveryCodes } from "./MfaSetup";
import "./platform.css";

const TABS = [
  { id: "overview", label: "Visão geral" },
  { id: "shops", label: "Barbearias" },
  { id: "feedback", label: "Feedback" },
  { id: "report", label: "Relatório" },
  { id: "health", label: "Saúde" },
  { id: "ai", label: "IA" },
  { id: "conversations", label: "Conversas" },
  { id: "metrics", label: "Métricas" },
  { id: "revenue", label: "Caixa" },
  { id: "audit", label: "Trilha" },
  { id: "security", label: "Segurança" },
] as const;
type TabId = (typeof TABS)[number]["id"];

export default function PlatformApp() {
  const [me, setMe] = useState<Me | null>(null);
  const [checking, setChecking] = useState(true);
  const [expired, setExpired] = useState(false);
  const [tab, setTab] = useState<TabId>(() => {
    const h = window.location.hash.replace("#", "");
    return (TABS.some((t) => t.id === h) ? h : "overview") as TabId;
  });

  const loadMe = useCallback(() => {
    setChecking(true);
    papi.get<Me>("/me")
      .then((m) => { setMe(m); setExpired(false); })
      .catch(() => setMe(null))
      .finally(() => setChecking(false));
  }, []);

  useEffect(() => {
    document.title = "Razor · Plataforma";
    loadMe();
    const lost = () => { setMe((cur) => { if (cur) setExpired(true); return null; }); };
    const mfa = () => loadMe();
    window.addEventListener(SESSION_LOST, lost);
    window.addEventListener(MFA_REQUIRED, mfa);
    return () => {
      window.removeEventListener(SESSION_LOST, lost);
      window.removeEventListener(MFA_REQUIRED, mfa);
    };
  }, [loadMe]);

  const go = (id: TabId) => {
    setTab(id);
    window.history.replaceState(null, "", `#${id}`);
  };

  const logout = async () => {
    await papi.post("/auth/logout").catch(() => {});
    setMe(null);
  };

  if (checking && !me) return <div className="plat-center muted">Carregando…</div>;
  if (!me) return <PlatformLogin onDone={loadMe} expired={expired} />;

  const sec = me.security;
  // Prazo vencido sem 2FA: só a configuração, nada do resto do console.
  if (sec.mfaRequiredNow) return <ForcedSetup onDone={loadMe} onLogout={logout} />;

  return (
    <div className="plat">
      <header className="plat-head">
        <div className="plat-brand">Razor <span>plataforma</span></div>
        <nav className="plat-tabs" aria-label="Seções do console">
          {TABS.map((t) => (
            <button key={t.id} className={tab === t.id ? "active" : ""} onClick={() => go(t.id)} aria-current={tab === t.id}>
              {t.label}{t.id === "security" && !sec.mfaEnabled ? " ⚠️" : ""}
            </button>
          ))}
        </nav>
        <div className="plat-user">
          <span className="muted">{me.email}</span>
          <button className="btn btn-ghost btn-sm" onClick={logout}>Sair</button>
        </div>
      </header>
      {!sec.mfaEnabled && (
        <div className="plat-mfa-banner" role="status">
          <span>
            <b>Ative a verificação em duas etapas.</b>{" "}
            {sec.mfaDaysLeft > 0
              ? <>Falta{sec.mfaDaysLeft === 1 ? "" : "m"} <b>{sec.mfaDaysLeft} dia{sec.mfaDaysLeft === 1 ? "" : "s"}</b> — depois o login só entra com o código do app.</>
              : <>O prazo acaba hoje.</>}
            {" "}Até lá, ler conversas, trocar a chave da IA e parear WhatsApp ficam bloqueados.
          </span>
          {tab !== "security" && <button className="btn btn-primary btn-sm" onClick={() => go("security")}>Ativar agora</button>}
        </div>
      )}
      <main className="plat-main">
        {tab === "overview" && <OverviewTab />}
        {tab === "shops" && <ShopsAdminTab />}
        {tab === "feedback" && <FeedbackTab />}
        {tab === "report" && <ReportTab />}
        {tab === "health" && <HealthTab />}
        {tab === "ai" && <AiTab />}
        {tab === "conversations" && <ConversationsTab />}
        {tab === "metrics" && <MetricsTab />}
        {tab === "revenue" && <RevenueTab />}
        {tab === "audit" && <AuditTab />}
        {tab === "security" && <SecurityTab onChanged={loadMe} onLoggedOutEverywhere={() => setMe(null)} />}
      </main>
      <IdleWarning idleMinutes={sec.idleMinutes} onKeepAlive={loadMe} />
    </div>
  );
}

/** Avisa 2 min antes de a sessão expirar por inatividade (conta pelo último uso da API). */
function IdleWarning({ idleMinutes, onKeepAlive }: { idleMinutes: number; onKeepAlive: () => void }) {
  const [left, setLeft] = useState<number | null>(null);
  useEffect(() => {
    const t = window.setInterval(() => {
      const remaining = idleMinutes * 60 - Math.floor((Date.now() - lastActivity) / 1000);
      setLeft(remaining <= 120 ? Math.max(0, remaining) : null);
      if (remaining <= 0) window.dispatchEvent(new Event(SESSION_LOST));
    }, 1000);
    return () => window.clearInterval(t);
  }, [idleMinutes]);
  if (left == null) return null;
  return (
    <div className="plat-idle" role="alertdialog" aria-live="assertive">
      <span>Sua sessão vai expirar por inatividade em <b>{Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}</b>.</span>
      <button className="btn btn-primary btn-sm" onClick={onKeepAlive}>Continuar conectado</button>
    </div>
  );
}

function ForcedSetup({ onDone, onLogout }: { onDone: () => void; onLogout: () => void }) {
  const [codes, setCodes] = useState<string[] | null>(null);
  return (
    <div className="plat-center">
      <div className="card plat-login" style={{ maxWidth: 520 }}>
        <div className="card-pad stack">
          <h2 style={{ margin: 0 }}>Ative a verificação em duas etapas</h2>
          {codes ? <RecoveryCodes codes={codes} onDone={onDone} /> : (
            <>
              <p className="muted" style={{ margin: 0 }}>O prazo acabou: para continuar usando o console, configure o app autenticador.</p>
              <MfaSetup onEnabled={setCodes} />
              <button className="btn btn-ghost btn-sm" style={{ justifySelf: "start" }} onClick={onLogout}>Sair</button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

type LoginStep = { step: "DONE" | "MFA" | "SETUP_REQUIRED"; challenge?: string };

function PlatformLogin({ onDone, expired }: { onDone: () => void; expired: boolean }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [step, setStep] = useState<LoginStep | null>(null);
  const [code, setCode] = useState("");
  const [useRecovery, setUseRecovery] = useState(false);
  const [recovery, setRecovery] = useState("");
  const [remember, setRemember] = useState(false);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await papi.post<LoginStep>("/auth/login", { email, password });
      setPassword("");
      if (r.step === "DONE") onDone();
      else setStep(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não consegui entrar.");
    } finally {
      setBusy(false);
    }
  };

  const submitCode = async (c = code) => {
    if (!step?.challenge || busy) return;
    setBusy(true);
    setError(null);
    try {
      await papi.post("/auth/verify", useRecovery
        ? { challenge: step.challenge, recoveryCode: recovery, rememberDevice: remember }
        : { challenge: step.challenge, code: c, rememberDevice: remember });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Código inválido.");
      setCode("");
      if (err instanceof ApiError && err.status === 401 && /expirad/i.test(err.message)) setStep(null);
    } finally {
      setBusy(false);
    }
  };

  const back = () => { setStep(null); setCode(""); setRecovery(""); setError(null); setUseRecovery(false); };

  return (
    <div className="plat-center">
      <div className="card plat-login" style={step?.step === "SETUP_REQUIRED" ? { maxWidth: 520 } : undefined}>
        <div className="card-pad stack">
          <h2 style={{ margin: 0 }}>Razor · Plataforma</h2>
          {expired && !step && <div className="muted" role="status">Sua sessão expirou. Entre de novo.</div>}
          {!step ? (
            <form className="stack" onSubmit={submitPassword}>
              <div className="field">
                <label>E-mail
                  <input className="input" type="email" autoComplete="username" value={email}
                    onChange={(e) => setEmail(e.target.value)} required />
                </label>
              </div>
              <div className="field">
                <label>Senha
                  <input className="input" type="password" autoComplete="current-password" value={password}
                    onChange={(e) => setPassword(e.target.value)} required />
                </label>
              </div>
              {error && <div className="error" role="alert">{error}</div>}
              <button className="btn btn-primary" disabled={busy}>{busy ? "Entrando…" : "Entrar"}</button>
            </form>
          ) : step.step === "SETUP_REQUIRED" ? (
            codes ? <RecoveryCodes codes={codes} onDone={onDone} /> : (
              <>
                <p style={{ margin: 0 }}>
                  O prazo para ativar a <b>verificação em duas etapas</b> acabou. Configure agora para entrar.
                </p>
                <MfaSetup challenge={step.challenge} onEnabled={setCodes} />
                <button type="button" className="btn btn-ghost btn-sm" style={{ justifySelf: "start" }} onClick={back}>Voltar</button>
              </>
            )
          ) : (
            <form className="stack" onSubmit={(e) => { e.preventDefault(); submitCode(); }}>
              {useRecovery ? (
                <div className="field">
                  <label>Código de recuperação
                    <input className="input" autoComplete="off" placeholder="xxxx-xxxx-xxxx" value={recovery}
                      onChange={(e) => setRecovery(e.target.value)} maxLength={20} />
                  </label>
                </div>
              ) : (
                <CodeInput value={code} onChange={setCode} autoSubmit={(v) => submitCode(v)} />
              )}
              <label className="row" style={{ gap: 8 }}>
                <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
                Lembrar este aparelho por 30 dias
              </label>
              {error && <div className="error" role="alert">{error}</div>}
              <div className="row wrap" style={{ gap: 8 }}>
                <button type="button" className="btn btn-ghost" onClick={back}>Voltar</button>
                <button className="btn btn-primary" disabled={busy || (useRecovery ? recovery.trim().length < 8 : code.length !== 6)}>
                  {busy ? "Conferindo…" : "Entrar"}
                </button>
              </div>
              <button type="button" className="btn btn-ghost btn-sm" style={{ justifySelf: "start" }}
                onClick={() => { setUseRecovery(!useRecovery); setError(null); }}>
                {useRecovery ? "Usar o código do app" : "Perdi o celular — usar um código de recuperação"}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
