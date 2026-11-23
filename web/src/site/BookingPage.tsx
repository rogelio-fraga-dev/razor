// Página pública de agendamento online: /b/:slug/agendar (V38).
//
// Sem login, fora do bloco autenticado do App, como o site. O passo a passo é uma página só, que
// vai se abrindo: serviço → barbeiro → dia → horário → nome e WhatsApp. Uma página só (e não um
// wizard de telas) porque o visitante vê de uma vez o que já escolheu e corrige qualquer passo
// sem "voltar" — e no celular é só rolar.
//
// A grade vem do MESMO motor do bot (backend: AgendaAvailabilityService). Esta página não sabe
// nada de expediente ou ocupação: pede os horários livres e mostra.
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { api, ApiError } from "../api/client";
import type { BookingConfirmation, BookingOptions, BookingSlots } from "../api/types";
import { money, whatsappHref } from "./parts";
import { safeHttpUrl } from "./safeUrl";
import "./site.css";
import "./booking.css";

const THEME_CLASS = { LIGHT: "theme-light", DARK: "theme-dark", AUTO: "theme-auto" } as const;
const SEM_PREFERENCIA = "";

/** yyyy-MM-dd no fuso do aparelho — o backend confere a janela de novo no fuso da barbearia. */
function isoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

const WEEKDAY = new Intl.DateTimeFormat("pt-BR", { weekday: "short" });
const DAY_MONTH = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" });
const LONG = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "long" });

/** "2026-11-20" → Date local, sem o deslocamento de fuso que `new Date(iso)` causaria. */
function parseIso(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** Máscara leve de WhatsApp: (11) 98888-7777. Só visual; o backend normaliza. */
function maskPhone(v: string): string {
  const d = v.replace(/\D/g, "").slice(0, 11);
  if (d.length <= 2) return d;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

function Step({ n, title, done, children }: { n: number; title: string; done?: boolean; children: React.ReactNode }) {
  return (
    <section className={`bk-step ${done ? "is-done" : ""}`} aria-labelledby={`bk-step-${n}`}>
      <h2 className="bk-step-title" id={`bk-step-${n}`}>
        <span className="bk-step-n" aria-hidden="true">{done ? "✓" : n}</span>
        {title}
      </h2>
      {children}
    </section>
  );
}

export function BookingPage() {
  const { slug = "" } = useParams();
  const base = `/api/site/${encodeURIComponent(slug)}/booking`;

  const [opts, setOpts] = useState<BookingOptions | null>(null);
  const [loadError, setLoadError] = useState(false);

  const [serviceId, setServiceId] = useState<string | null>(null);
  const [professionalId, setProfessionalId] = useState<string>(SEM_PREFERENCIA);
  const [date, setDate] = useState<string | null>(null);
  const [slots, setSlots] = useState<string[] | null>(null);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [time, setTime] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [website, setWebsite] = useState(""); // honeypot — escondido de gente, visível a robô
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<BookingConfirmation | null>(null);
  const [slotsVersion, setSlotsVersion] = useState(0);

  const timeRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let vivo = true;
    api
      .get<BookingOptions>(base)
      .then((o) => {
        if (!vivo) return;
        setOpts(o);
        if (o.services.length === 1) setServiceId(o.services[0].id);
        document.title = `Agendar · ${o.barbershopName}`;
      })
      .catch(() => vivo && setLoadError(true));
    return () => {
      vivo = false;
    };
  }, [base]);

  // Grade do dia: recarrega a cada troca de serviço, barbeiro ou dia (e após um 409).
  useEffect(() => {
    if (!serviceId || !date) return;
    let vivo = true;
    setSlotsLoading(true);
    setSlots(null);
    const qs = new URLSearchParams({ serviceId, date });
    if (professionalId) qs.set("professionalId", professionalId);
    api
      .get<BookingSlots>(`${base}/slots?${qs}`)
      .then((r) => vivo && setSlots(r.slots))
      .catch((e) => vivo && (setSlots([]), setError(e instanceof ApiError ? e.message : "Não foi possível carregar os horários.")))
      .finally(() => vivo && setSlotsLoading(false));
    return () => {
      vivo = false;
    };
  }, [base, serviceId, professionalId, date, slotsVersion]);

  // Horário escolhido que sumiu da grade nova deixa de valer.
  useEffect(() => {
    if (time && slots && !slots.includes(time)) setTime(null);
  }, [slots, time]);

  const days = useMemo(() => {
    if (!opts) return [];
    const out: { iso: string; d: Date; open: boolean }[] = [];
    const today = new Date();
    for (let i = 0; i < opts.maxDaysAhead; i++) {
      const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() + i);
      const iso = ((d.getDay() + 6) % 7) + 1; // JS 0=dom → ISO 1=seg … 7=dom
      out.push({ iso: isoDate(d), d, open: opts.openWeekdays.includes(iso) });
    }
    return out;
  }, [opts]);

  const service = opts?.services.find((s) => s.id === serviceId) ?? null;
  const professional = opts?.professionals.find((p) => p.id === professionalId) ?? null;
  const askProfessional = (opts?.professionals.length ?? 0) > 1;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!serviceId || !date || !time) return;
    setSending(true);
    setError(null);
    try {
      const r = await api.post<BookingConfirmation>(base, {
        serviceId,
        professionalId: professionalId || null,
        date,
        time,
        name,
        phone,
        website,
      });
      setDone(r);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // Alguém levou o horário entre a grade aparecer e o clique: grade nova, mesma tela.
        setTime(null);
        setSlotsVersion((v) => v + 1);
        timeRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      setError(err instanceof ApiError ? err.message : "Não foi possível agendar. Tente de novo.");
    } finally {
      setSending(false);
    }
  };

  if (loadError) {
    return (
      <div className="rzsite theme-auto bk-center">
        <div>
          <h1 className="rz-h2">Agendamento indisponível</h1>
          <p className="rz-lead">Esta barbearia não está recebendo agendamentos pelo site agora.</p>
          <p style={{ marginTop: 18 }}>
            <Link className="rz-btn rz-btn-ghost" to={`/b/${encodeURIComponent(slug)}`}>Voltar ao site</Link>
          </p>
        </div>
      </div>
    );
  }
  if (!opts) return <div className="rzsite theme-auto" style={{ minHeight: "100vh" }} />;

  const themeClass = THEME_CLASS[opts.theme] ?? "theme-auto";
  const logo = safeHttpUrl(opts.logoUrl);
  const wa = whatsappHref(opts);
  const siteHref = `/b/${encodeURIComponent(opts.slug)}`;

  return (
    <div className={`rzsite ${themeClass} bk`} style={{ ["--accent" as string]: opts.accentColor }}>
      <header className="bk-top">
        <div className="bk-wrap bk-top-inner">
          <Link to={siteHref} className="bk-brand">
            {logo ? <img src={logo} alt={opts.barbershopName} /> : <strong>{opts.barbershopName}</strong>}
          </Link>
          <Link to={siteHref} className="bk-back">← Voltar ao site</Link>
        </div>
      </header>

      <main className="bk-wrap">
        {done ? (
          <div className="bk-done" role="status">
            <div className="bk-done-icon" aria-hidden="true">✓</div>
            <h1 className="rz-h2">Horário confirmado!</h1>
            <p className="rz-lead">
              {done.serviceName}
              {done.professionalName ? ` com ${done.professionalName}` : ""}
              <br />
              <strong style={{ color: "var(--text)" }}>
                {LONG.format(parseIso(done.date))} às {done.time}
              </strong>
            </p>
            <p className="bk-muted">
              Mandamos a confirmação no seu WhatsApp, e você recebe um lembrete uma hora antes. Precisa
              cancelar ou remarcar? É só responder <strong>cancelar</strong> naquela conversa.
            </p>
            <Link className="rz-btn rz-btn-primary" to={siteHref}>Voltar ao site</Link>
          </div>
        ) : (
          <form onSubmit={submit} noValidate>
            <h1 className="bk-title">Agende seu horário</h1>
            <p className="bk-muted" style={{ marginTop: 6 }}>
              Escolha o serviço, o dia e um horário livre. Leva menos de um minuto, sem cadastro.
            </p>

            <Step n={1} title="Serviço" done={!!service}>
              <div className="bk-options" role="radiogroup" aria-label="Serviço">
                {opts.services.map((s) => (
                  <button
                    type="button"
                    key={s.id}
                    role="radio"
                    aria-checked={serviceId === s.id}
                    className={`bk-option ${serviceId === s.id ? "is-on" : ""}`}
                    onClick={() => setServiceId(s.id)}
                  >
                    <span className="bk-option-name">{s.name}</span>
                    <span className="bk-option-meta">
                      {s.durationMinutes} min{s.price != null ? ` · ${money(s.price)}` : ""}
                    </span>
                  </button>
                ))}
              </div>
              {opts.services.length === 0 && <p className="bk-muted">Nenhum serviço disponível no momento.</p>}
            </Step>

            {service && askProfessional && (
              <Step n={2} title="Com quem?" done>
                <div className="bk-options" role="radiogroup" aria-label="Profissional">
                  <button
                    type="button"
                    role="radio"
                    aria-checked={professionalId === SEM_PREFERENCIA}
                    className={`bk-option ${professionalId === SEM_PREFERENCIA ? "is-on" : ""}`}
                    onClick={() => setProfessionalId(SEM_PREFERENCIA)}
                  >
                    <span className="bk-option-name">Sem preferência</span>
                    <span className="bk-option-meta">mais horários livres</span>
                  </button>
                  {opts.professionals.map((p) => (
                    <button
                      type="button"
                      key={p.id}
                      role="radio"
                      aria-checked={professionalId === p.id}
                      className={`bk-option ${professionalId === p.id ? "is-on" : ""}`}
                      onClick={() => setProfessionalId(p.id)}
                    >
                      <span className="bk-option-name">{p.name}</span>
                    </button>
                  ))}
                </div>
              </Step>
            )}

            {service && (
              <Step n={askProfessional ? 3 : 2} title="Dia" done={!!date}>
                <div className="bk-days" role="radiogroup" aria-label="Dia">
                  {days.map(({ iso, d, open }, i) => (
                    <button
                      type="button"
                      key={iso}
                      role="radio"
                      aria-checked={date === iso}
                      disabled={!open}
                      className={`bk-day ${date === iso ? "is-on" : ""}`}
                      onClick={() => {
                        setDate(iso);
                        setError(null);
                        setTimeout(() => timeRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" }), 50);
                      }}
                      title={open ? undefined : "Fechado"}
                    >
                      <span className="bk-day-w">{i === 0 ? "hoje" : i === 1 ? "amanhã" : WEEKDAY.format(d).replace(".", "")}</span>
                      <span className="bk-day-d">{DAY_MONTH.format(d).replace(".", "").replace(" de ", " ")}</span>
                    </button>
                  ))}
                </div>
              </Step>
            )}

            {service && date && (
              <div ref={timeRef}>
                <Step n={askProfessional ? 4 : 3} title="Horário" done={!!time}>
                  {slotsLoading && <p className="bk-muted">Buscando horários livres…</p>}
                  {slots && slots.length === 0 && !slotsLoading && (
                    <p className="bk-muted">
                      Nenhum horário livre neste dia{professional ? ` com ${professional.name}` : ""}. Tente outro dia
                      {professional ? " ou “Sem preferência”" : ""}.
                    </p>
                  )}
                  {slots && slots.length > 0 && (
                    <div className="bk-slots" role="radiogroup" aria-label="Horário">
                      {slots.map((s) => (
                        <button
                          type="button"
                          key={s}
                          role="radio"
                          aria-checked={time === s}
                          className={`bk-slot ${time === s ? "is-on" : ""}`}
                          onClick={() => {
                            setTime(s);
                            setError(null);
                            setTimeout(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" }), 50);
                          }}
                        >
                          {s}
                        </button>
                      ))}
                    </div>
                  )}
                </Step>
              </div>
            )}

            {service && date && time && (
              <div ref={formRef}>
                <Step n={askProfessional ? 5 : 4} title="Seus dados">
                  <div className="bk-fields">
                    <label className="bk-field">
                      <span>Nome</span>
                      <input
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        autoComplete="name"
                        maxLength={80}
                        required
                        placeholder="Como podemos te chamar"
                      />
                    </label>
                    <label className="bk-field">
                      <span>WhatsApp</span>
                      <input
                        value={phone}
                        onChange={(e) => setPhone(maskPhone(e.target.value))}
                        autoComplete="tel-national"
                        inputMode="tel"
                        required
                        placeholder="(11) 98888-7777"
                      />
                    </label>
                    {/* Honeypot: fora da tela e fora da navegação por teclado/leitor. Gente não
                        preenche; robô que preenche todo input cai aqui e o pedido é descartado. */}
                    <label className="bk-hp" aria-hidden="true">
                      Site
                      <input tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} />
                    </label>
                  </div>

                  <div className="bk-summary">
                    <strong>{service.name}</strong>
                    {professional ? ` com ${professional.name}` : ""} · {LONG.format(parseIso(date))} às{" "}
                    <strong>{time}</strong>
                  </div>

                  {error && <div className="bk-error" role="alert">{error}</div>}

                  <button
                    type="submit"
                    className="rz-btn rz-btn-primary bk-submit"
                    disabled={sending || name.trim().length < 2 || phone.replace(/\D/g, "").length < 10}
                  >
                    {sending ? "Agendando…" : "Confirmar agendamento"}
                  </button>
                  <p className="bk-muted bk-fine">
                    A confirmação chega no seu WhatsApp. Usamos o número só para falar sobre este horário.
                  </p>
                </Step>
              </div>
            )}

            {error && !(service && date && time) && <div className="bk-error" role="alert">{error}</div>}

            {wa && (
              <p className="bk-muted bk-alt">
                Prefere conversar?{" "}
                <a href={wa} target="_blank" rel="noopener noreferrer">Agende pelo WhatsApp</a>
              </p>
            )}
          </form>
        )}
      </main>
    </div>
  );
}
