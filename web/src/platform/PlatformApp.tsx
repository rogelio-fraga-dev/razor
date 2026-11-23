// Console da plataforma (V51) — acesso do dono do Razor, separado do painel das barbearias:
// sessão própria (cookie só em /api/platform) e 2FA obrigatório. Carregado sob demanda: quem usa
// o painel da barbearia nunca baixa este código.
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";
import { papi } from "./api";
import { OverviewTab, HealthTab, AiTab, AuditTab } from "./PlatformPages";
import { ConversationsTab, MetricsTab, RevenueTab } from "./PlatformData";
import "./platform.css";

type Me = { name: string; email: string };

const TABS = [
  { id: "overview", label: "Visão geral" },
  { id: "health", label: "Saúde" },
  { id: "ai", label: "IA" },
  { id: "conversations", label: "Conversas" },
  { id: "metrics", label: "Métricas" },
  { id: "revenue", label: "Caixa" },
  { id: "audit", label: "Trilha" },
] as const;
type TabId = (typeof TABS)[number]["id"];

export default function PlatformApp() {
  const [me, setMe] = useState<Me | null>(null);
  const [checking, setChecking] = useState(true);
  const [tab, setTab] = useState<TabId>(() => {
    const h = window.location.hash.replace("#", "");
    return (TABS.some((t) => t.id === h) ? h : "overview") as TabId;
  });

  const loadMe = useCallback(() => {
    setChecking(true);
    papi.get<Me>("/me")
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setChecking(false));
  }, []);

  useEffect(() => {
    document.title = "Razor · Plataforma";
    loadMe();
  }, [loadMe]);

  const go = (id: TabId) => {
    setTab(id);
    window.history.replaceState(null, "", `#${id}`);
  };

  const logout = async () => {
    await papi.post("/auth/logout").catch(() => {});
    setMe(null);
  };

  if (checking) return <div className="plat-center muted">Carregando…</div>;
  if (!me) return <PlatformLogin onDone={loadMe} />;

  return (
    <div className="plat">
      <header className="plat-head">
        <div className="plat-brand">
          Razor <span>plataforma</span>
        </div>
        <nav className="plat-tabs" aria-label="Seções do console">
          {TABS.map((t) => (
            <button key={t.id} className={tab === t.id ? "active" : ""} onClick={() => go(t.id)} aria-current={tab === t.id}>
              {t.label}
            </button>
          ))}
        </nav>
        <div className="plat-user">
          <span className="muted">{me.email}</span>
          <button className="btn btn-ghost btn-sm" onClick={logout}>Sair</button>
        </div>
      </header>
      <main className="plat-main">
        {tab === "overview" && <OverviewTab />}
        {tab === "health" && <HealthTab />}
        {tab === "ai" && <AiTab />}
        {tab === "conversations" && <ConversationsTab />}
        {tab === "metrics" && <MetricsTab />}
        {tab === "revenue" && <RevenueTab />}
        {tab === "audit" && <AuditTab />}
      </main>
    </div>
  );
}

type LoginStep = { step: "MFA" | "SETUP"; challenge: string; secret: string | null; otpauthUri: string | null };

function PlatformLogin({ onDone }: { onDone: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [step, setStep] = useState<LoginStep | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setStep(await papi.post<LoginStep>("/auth/login", { email, password }));
      setPassword("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não consegui entrar.");
    } finally {
      setBusy(false);
    }
  };

  const submitCode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!step) return;
    setBusy(true);
    setError(null);
    try {
      await papi.post("/auth/verify", { challenge: step.challenge, code });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Código inválido.");
      setCode("");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="plat-center">
      <div className="card plat-login">
        <div className="card-pad stack">
          <h2 style={{ margin: 0 }}>Razor · Plataforma</h2>
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
              <button className="btn btn-primary" disabled={busy}>{busy ? "Entrando…" : "Continuar"}</button>
            </form>
          ) : (
            <form className="stack" onSubmit={submitCode}>
              {step.step === "SETUP" && step.secret && (
                <div className="stack" style={{ gap: 8 }}>
                  <p style={{ margin: 0 }}>
                    <b>Primeiro acesso:</b> ative a verificação em duas etapas. No app autenticador (Google
                    Authenticator, Authy, 1Password…), escolha <em>adicionar conta → inserir chave</em> e digite:
                  </p>
                  <code className="plat-secret">{step.secret.match(/.{1,4}/g)?.join(" ")}</code>
                  <p className="muted" style={{ margin: 0, fontSize: 13 }}>
                    Tipo: baseado em tempo · 6 dígitos. No celular, você também pode{" "}
                    {step.otpauthUri && <a href={step.otpauthUri}>abrir direto no app</a>}.
                    Guarde a chave num lugar seguro — ela não aparece de novo.
                  </p>
                </div>
              )}
              <div className="field">
                <label>Código de 6 dígitos do app
                  <input className="input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} autoFocus
                    value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} required />
                </label>
              </div>
              {error && <div className="error" role="alert">{error}</div>}
              <div className="row" style={{ gap: 8 }}>
                <button type="button" className="btn btn-ghost" onClick={() => { setStep(null); setCode(""); setError(null); }}>
                  Voltar
                </button>
                <button className="btn btn-primary" disabled={busy || code.length !== 6}>
                  {busy ? "Conferindo…" : "Entrar"}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
