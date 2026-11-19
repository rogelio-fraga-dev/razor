import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { RazorLogo } from "../components/Logo";

/** Nome comercial dos planos da landing, para confirmar a escolha de quem veio de lá. */
const PLANOS: Record<string, string> = {
  solo: "Solo",
  equipe: "Equipe",
  pro: "Pro",
};

/**
 * Endereco da landing, injetado no build (ver web/Dockerfile). Vazio esconde o link de
 * volta: quem abre o painel sozinho, sem o site no ar, nao deve ver um caminho quebrado.
 */
const SITE_URL = (import.meta.env.VITE_SITE_URL ?? "").trim();

export function Login() {
  const { login } = useAuth();
  // Links antigos da landing ainda mandam ?plano=… para cá: oferecemos o cadastro (V49) sem
  // descartar a escolha.
  const planoEscolhido = PLANOS[new URLSearchParams(window.location.search).get("plano") ?? ""];
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível entrar.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="brand">
          <RazorLogo size={24} />
        </div>
        <span className="auth-tagline">O sistema operacional da sua barbearia</span>
        <h2 style={{ marginBottom: 4 }}>Entrar no painel</h2>
        <p className="muted" style={{ margin: "0 0 22px", fontSize: 14 }}>
          Configure o bot, acompanhe o caixa e opere no automático.
        </p>

        {planoEscolhido && (
          <div
            className="card-pad"
            style={{
              marginBottom: 18,
              border: "1px solid var(--border-hi)",
              borderRadius: "var(--radius-sm)",
              background: "var(--surface-2)",
            }}
          >
            <strong>Plano {planoEscolhido}</strong>
            <p className="muted" style={{ margin: "4px 0 0", fontSize: 13.5 }}>
              Ainda não tem conta?{" "}
              <Link to={`/cadastro${window.location.search}`}>Comece o teste grátis de 14 dias</Link>. Já
              tem? Entre abaixo.
            </p>
          </div>
        )}
        <form className="stack" onSubmit={submit}>
          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <div className="error" role="alert">{error}</div>}
          <button className="btn btn-primary" type="submit" disabled={busy} style={{ marginTop: 4 }}>
            {busy ? "Entrando…" : "Entrar"}
          </button>
        </form>

        {!planoEscolhido && (
          <p className="muted" style={{ margin: "18px 0 0", fontSize: 13.5, textAlign: "center" }}>
            Ainda não tem conta? <Link to="/cadastro">Teste grátis por 14 dias</Link>
          </p>
        )}
        {SITE_URL && (
          <p className="muted" style={{ margin: "18px 0 0", fontSize: 13.5, textAlign: "center" }}>
            <a href={SITE_URL}>← Voltar para o site</a>
          </p>
        )}
      </div>
    </div>
  );
}
