import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { RazorLogo } from "../components/Logo";

/** Nome comercial dos planos da landing, para confirmar a escolha de quem veio de lá. */
const PLANOS: Record<string, string> = {
  solo: "Solo",
  equipe: "Equipe",
  pro: "Pro",
};

export function Login() {
  const { login } = useAuth();
  // A landing manda ?plano=… nos CTAs. Enquanto o cadastro self-service não existe,
  // reconhecemos a intenção em vez de descartá-la silenciosamente.
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
              O cadastro pela web ainda está em construção. Já tem conta? Entre abaixo. Se
              ainda não tem, fale com a gente pelo WhatsApp que abrimos sua barbearia.
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
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" type="submit" disabled={busy} style={{ marginTop: 4 }}>
            {busy ? "Entrando…" : "Entrar"}
          </button>
        </form>
      </div>
    </div>
  );
}
