// Cadastro da barbearia e do dono numa tela só, sem cartão.
//
// Piloto (26/11): o cadastro pode estar "só por convite" — quem chega sem o link
// /cadastro?convite=CODIGO vê o convite para se inscrever pelo Instagram. Com o link, o plano
// vira PILOTO (o que libera e o que não libera está em PlanFeatures no backend). Depois do
// cadastro a sessão já está aberta e o dono cai direto no assistente guiado (/comecar).
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { ApiError, api } from "../api/client";
import { RazorLogo } from "../components/Logo";
import { signupFlow } from "../api/onboarding";
import "./signup.css";

const PLANOS: Record<string, { nome: string; resumo: string }> = {
  solo: { nome: "Solo", resumo: "1 barbeiro" },
  equipe: { nome: "Equipe", resumo: "até 5 barbeiros" },
  pro: { nome: "Pro", resumo: "até 10 barbeiros" },
  piloto: { nome: "Piloto", resumo: "grátis por 14 dias" },
};

/** Endereço da landing (build). Vazio = sem links para os termos (não há site para apontar). */
const SITE_URL = (import.meta.env.VITE_SITE_URL ?? "").trim().replace(/\/$/, "");
const INSTAGRAM = "https://www.instagram.com/rogeliofraga.dev/";

/** (21) 97000-1111 enquanto digita — só visual; o backend normaliza de novo. */
function maskPhone(v: string): string {
  const d = v.replace(/\D/g, "").replace(/^55(?=\d{10,11}$)/, "").slice(0, 11);
  if (d.length <= 2) return d.length ? `(${d}` : "";
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

type Status = { enabled: boolean; inviteOnly: boolean } | null;

export function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const params = new URLSearchParams(window.location.search);
  const invite = (params.get("convite") ?? "").trim();
  // Convite é sempre do piloto; sem convite vale o plano do link da landing.
  const planoParam = invite ? "piloto" : (params.get("plano") ?? "").toLowerCase();
  const plano = PLANOS[planoParam];

  const [status, setStatus] = useState<Status>(null);
  useEffect(() => {
    api.get<{ enabled: boolean; inviteOnly: boolean }>("/api/signup/status", { ignore401: true })
      .then(setStatus)
      .catch(() => setStatus({ enabled: true, inviteOnly: false }));
  }, []);

  const [form, setForm] = useState({ shopName: "", ownerName: "", email: "", password: "", whatsapp: "", city: "", website: "" });
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
    // A sessão nasce dentro do signup(); com a marca, a rota /cadastro manda para o assistente.
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
        invite: invite || null,
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
    SITE_URL ? <a href={`${SITE_URL}/${file}`} target="_blank" rel="noreferrer">{label}</a> : label;

  const closed = status && (!status.enabled || (status.inviteOnly && !invite));

  return (
    <div className="auth-wrap su-wrap">
      <div className="su-shell">
        <aside className="su-pitch">
          <RazorLogo size={28} />
          <h1>Sua barbearia no <em>piloto automático</em>.</h1>
          <p className="su-lead">A IA atende o WhatsApp, marca no barbeiro certo e cobra o sinal. Você cuida do corte.</p>
          <ul className="su-steps">
            <li><span className="su-n mono">01</span><div><b>Crie a conta</b><span>1 minuto, sem cartão</span></div></li>
            <li><span className="su-n mono">02</span><div><b>Cadastre horário, serviços e equipe</b><span>o assistente guia cada passo</span></div></li>
            <li><span className="su-n mono">03</span><div><b>Conecte o WhatsApp da barbearia</b><span>um número só da barbearia, pelo QR code</span></div></li>
            <li><span className="su-n mono">04</span><div><b>Mande um “oi” e veja a IA atender</b><span>a agenda começa a encher</span></div></li>
          </ul>
          <p className="su-trust mono">14 DIAS GRÁTIS · SEM CARTÃO · SEM FIDELIDADE</p>
        </aside>

        <div className="auth-card su-card">
          <div className="brand su-brand-mobile"><RazorLogo size={24} /></div>

          {status === null ? (
            <p className="muted" role="status">Carregando…</p>
          ) : closed ? (
            <div className="su-closed">
              <span className="auth-tagline">Piloto · 5 vagas</span>
              <h2>Cadastro por convite</h2>
              <p className="muted">
                Estamos escolhendo as 5 barbearias do piloto grátis. Para participar, assista ao vídeo no Instagram,
                comente <b>EU QUERO</b> e siga o perfil. <b>O resultado sai dia 02/12.</b>
              </p>
              <a className="btn btn-primary su-submit" href={INSTAGRAM} target="_blank" rel="noreferrer">Ver o vídeo no Instagram</a>
              <p className="muted su-foot">Já tem conta? <Link to="/login">Entrar</Link></p>
            </div>
          ) : (
            <>
              <span className="auth-tagline">{invite ? "Convite do piloto" : "Teste grátis de 14 dias"}</span>
              <h2 className="su-title">Criar minha barbearia</h2>
              <p className="muted su-sub">Sem cartão. Você configura tudo em seguida, passo a passo.</p>

              {plano && (
                <div className="su-plan" role="status">
                  <span className="badge badge-plan">Plano {plano.nome}</span>
                  <span className="faint">{plano.resumo}</span>
                </div>
              )}

              <form className="su-form" onSubmit={submit} noValidate>
                <fieldset className="su-group">
                  <legend>Sua barbearia</legend>
                  <div className="su-row">
                    <div className="field">
                      <label htmlFor="su-shop">Nome da barbearia</label>
                      <input id="su-shop" className="input" value={form.shopName} onChange={set("shopName")}
                        autoComplete="organization" placeholder="Ex.: Barbearia do Zé" required maxLength={120} />
                    </div>
                    <div className="field">
                      <label htmlFor="su-city">Cidade</label>
                      <input id="su-city" className="input" value={form.city} onChange={set("city")}
                        autoComplete="address-level2" placeholder="Ex.: Araguari" required maxLength={120} />
                    </div>
                  </div>
                </fieldset>

                <fieldset className="su-group">
                  <legend>Você e o seu acesso</legend>
                  <div className="su-row">
                    <div className="field">
                      <label htmlFor="su-owner">Seu nome</label>
                      <input id="su-owner" className="input" value={form.ownerName} onChange={set("ownerName")}
                        autoComplete="name" required maxLength={120} />
                    </div>
                    <div className="field">
                      <label htmlFor="su-phone">Seu WhatsApp pessoal</label>
                      <input id="su-phone" className="input" value={form.whatsapp} onChange={set("whatsapp")}
                        type="tel" inputMode="tel" autoComplete="tel-national" placeholder="(34) 99999-0000" required />
                    </div>
                  </div>
                  <span className="su-help">
                    Por ele você recebe os avisos. O bot atende num <b>número só da barbearia</b> (de preferência WhatsApp
                    Business), que você conecta depois.
                  </span>
                  <div className="su-row">
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
                          aria-pressed={showPassword} aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"}>
                          {showPassword ? "Ocultar" : "Ver"}
                        </button>
                      </div>
                      <span id="su-pass-help" className={`su-help${passwordShort ? " su-warn" : ""}`}>Pelo menos 8 caracteres.</span>
                    </div>
                  </div>
                </fieldset>

                {/* Honeypot: fora da tela e do teclado. Robô que preenche tudo é recusado no backend. */}
                <div className="su-hp" aria-hidden="true">
                  <label htmlFor="su-website">Site</label>
                  <input id="su-website" tabIndex={-1} autoComplete="off" value={form.website} onChange={set("website")} />
                </div>

                <label className="su-accept">
                  <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
                  <span>Li e aceito os {legal("termos.html", "Termos de uso")} e a {legal("privacidade.html", "Política de privacidade")}.</span>
                </label>

                {error && <div className="error" role="alert">{error}</div>}
                <button className="btn btn-primary su-submit" type="submit" disabled={busy}>
                  {busy ? "Criando sua barbearia…" : "Criar conta e começar"}
                </button>
              </form>
              <p className="muted su-foot">Já tem conta? <Link to="/login">Entrar</Link></p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
