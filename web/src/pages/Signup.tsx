// Cadastro sozinho pela landing (V49): a barbearia e o dono numa tela só, sem cartão.
//
// Os botões dos planos na landing chegam aqui com ?plano=solo|equipe|pro. O plano só fica
// registrado como interesse — o teste grátis não cobra nada e não bloqueia nada no fim (a
// cobrança da assinatura é decisão pendente). Depois do cadastro a sessão já está aberta e o
// dono cai direto no assistente guiado (/comecar).
import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { RazorLogo } from "../components/Logo";
import { signupFlow } from "../api/onboarding";
import "./signup.css";

const PLANOS: Record<string, { nome: string; resumo: string }> = {
  solo: { nome: "Solo", resumo: "1 barbeiro" },
  equipe: { nome: "Equipe", resumo: "até 5 barbeiros" },
  pro: { nome: "Pro", resumo: "até 10 barbeiros" },
  piloto: { nome: "Piloto", resumo: "grátis por 14 dias, com tudo liberado" },
};

/** Endereço da landing (build). Vazio = sem links para os termos (não há site para apontar). */
const SITE_URL = (import.meta.env.VITE_SITE_URL ?? "").trim().replace(/\/$/, "");

/** (21) 97000-1111 enquanto digita — só visual; o backend normaliza de novo. */
function maskPhone(v: string): string {
  const d = v.replace(/\D/g, "").replace(/^55(?=\d{10,11}$)/, "").slice(0, 11);
  if (d.length <= 2) return d.length ? `(${d}` : "";
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

export function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const planoParam = (new URLSearchParams(window.location.search).get("plano") ?? "").toLowerCase();
  const plano = PLANOS[planoParam];

  const [form, setForm] = useState({
    shopName: "",
    ownerName: "",
    email: "",
    password: "",
    whatsapp: "",
    city: "",
    website: "",
  });
  const [accepted, setAccepted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: k === "whatsapp" ? maskPhone(e.target.value) : e.target.value }));

  const passwordShort = form.password.length > 0 && form.password.length < 8;
  const phoneDigits = form.whatsapp.replace(/\D/g, "");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (form.password.length < 8) return setError("A senha precisa de pelo menos 8 caracteres.");
    if (phoneDigits.length < 10) return setError("Informe seu WhatsApp com DDD.");
    if (!accepted) return setError("Para continuar, aceite os Termos de uso e a Política de privacidade.");
    setBusy(true);
    // A sessão nasce dentro do signup(); com a marca, a rota /cadastro manda para o assistente
    // (e não para o Painel) no instante em que o usuário vira logado.
    signupFlow.done = true;
    try {
      await signup({
        shopName: form.shopName.trim(),
        ownerName: form.ownerName.trim(),
        email: form.email.trim(),
        password: form.password,
        whatsapp: phoneDigits,
        city: form.city.trim(),
        plan: plano ? planoParam : null,
        website: form.website,
      });
      navigate("/comecar", { replace: true });
    } catch (err) {
      signupFlow.done = false;
      setError(err instanceof ApiError ? err.message : "Não foi possível criar a conta. Tente de novo.");
    } finally {
      setBusy(false);
    }
  };

  const legal = (file: string, label: string) =>
    SITE_URL ? (
      <a href={`${SITE_URL}/${file}`} target="_blank" rel="noreferrer">
        {label}
      </a>
    ) : (
      label
    );

  return (
    <div className="auth-wrap su-wrap">
      <div className="su-shell">
        <aside className="su-pitch" aria-hidden="true">
          <RazorLogo size={26} />
          <h1>
            Sua barbearia no <em>piloto automático</em> em 10 minutos.
          </h1>
          <ul className="su-steps">
            <li><span className="mono">01</span> Crie a conta</li>
            <li><span className="mono">02</span> Cadastre horário, serviços e equipe</li>
            <li><span className="mono">03</span> Conecte o WhatsApp pelo QR</li>
            <li><span className="mono">04</span> Mande um “oi” e veja a IA atender</li>
          </ul>
          <p className="su-trust mono">14 DIAS GRÁTIS · SEM CARTÃO · SEM FIDELIDADE</p>
        </aside>

        <div className="auth-card su-card">
          <div className="brand su-brand-mobile">
            <RazorLogo size={24} />
          </div>
          <span className="auth-tagline">Teste grátis de 14 dias</span>
          <h2 style={{ marginBottom: 4 }}>Criar minha barbearia</h2>
          <p className="muted" style={{ margin: "0 0 18px", fontSize: 14 }}>
            Sem cartão. Você configura tudo em seguida, passo a passo.
          </p>

          {plano && (
            <div className="su-plan" role="status">
              <span className="badge badge-plan">Plano {plano.nome}</span>
              <span className="faint">{plano.resumo} · escolha confirmada no fim do teste</span>
            </div>
          )}

          <form className="stack su-form" onSubmit={submit} noValidate>
            <div className="field">
              <label htmlFor="su-shop">Nome da barbearia</label>
              <input id="su-shop" className="input" value={form.shopName} onChange={set("shopName")}
                autoComplete="organization" placeholder="Ex.: Barbearia do Zé" required maxLength={120} />
            </div>
            <div className="su-row">
              <div className="field">
                <label htmlFor="su-owner">Seu nome</label>
                <input id="su-owner" className="input" value={form.ownerName} onChange={set("ownerName")}
                  autoComplete="name" required maxLength={120} />
              </div>
              <div className="field">
                <label htmlFor="su-city">Cidade</label>
                <input id="su-city" className="input" value={form.city} onChange={set("city")}
                  autoComplete="address-level2" placeholder="Ex.: Niterói" required maxLength={120} />
              </div>
            </div>
            <div className="field">
              <label htmlFor="su-phone">Seu WhatsApp (com DDD)</label>
              <input id="su-phone" className="input" value={form.whatsapp} onChange={set("whatsapp")}
                type="tel" inputMode="tel" autoComplete="tel-national" placeholder="(21) 97000-1111" required />
              <span className="su-help">O seu número pessoal: por ele você comanda o bot e recebe os avisos.</span>
            </div>
            <div className="field">
              <label htmlFor="su-email">E-mail</label>
              <input id="su-email" className="input" type="email" value={form.email} onChange={set("email")}
                autoComplete="username" required maxLength={255} />
            </div>
            <div className="field">
              <label htmlFor="su-pass">Senha</label>
              <div className="su-pass">
                <input id="su-pass" className="input" type={showPassword ? "text" : "password"}
                  value={form.password} onChange={set("password")} autoComplete="new-password"
                  required minLength={8} maxLength={100} aria-describedby="su-pass-help"
                  aria-invalid={passwordShort || undefined} />
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowPassword((v) => !v)}
                  aria-pressed={showPassword}>
                  {showPassword ? "Ocultar" : "Mostrar"}
                </button>
              </div>
              <span id="su-pass-help" className={`su-help${passwordShort ? " su-warn" : ""}`}>
                Pelo menos 8 caracteres.
              </span>
            </div>

            {/* Honeypot: fora da tela e fora do teclado. Gente não vê e não preenche; robô
                que preenche tudo é recusado no backend com mensagem genérica. */}
            <div className="su-hp" aria-hidden="true">
              <label htmlFor="su-website">Site</label>
              <input id="su-website" tabIndex={-1} autoComplete="off" value={form.website} onChange={set("website")} />
            </div>

            <label className="su-accept">
              <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
              <span>
                Li e aceito os {legal("termos.html", "Termos de uso")} e a{" "}
                {legal("privacidade.html", "Política de privacidade")}.
              </span>
            </label>

            {error && <div className="error" role="alert">{error}</div>}
            <button className="btn btn-primary su-submit" type="submit" disabled={busy}>
              {busy ? "Criando sua barbearia…" : "Criar conta e começar"}
            </button>
          </form>

          <p className="muted" style={{ margin: "18px 0 0", fontSize: 13.5, textAlign: "center" }}>
            Já tem conta? <Link to="/login">Entrar</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
