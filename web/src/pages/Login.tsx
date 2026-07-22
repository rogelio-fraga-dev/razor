import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";

export function Login() {
  const { login } = useAuth();
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
          <span className="mark">R</span>
          <span>Razor</span>
        </div>
        <h2 style={{ marginBottom: 4 }}>Entrar no painel</h2>
        <p className="muted" style={{ margin: "0 0 22px", fontSize: 14 }}>
          Gerencie sua barbearia: serviços, comissões e relatórios.
        </p>
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
