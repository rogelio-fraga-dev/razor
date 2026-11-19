// Assistente guiado de 10 minutos (V49), em tela cheia, logo depois do cadastro pela landing.
//
// Casa → horário → serviços → equipe → WhatsApp (QR) → teste do bot → site. Não duplica o modal
// de configuração (ConfigWizard): usa os mesmos blocos — IdentityFields/HoursFields da config do
// bot, o ServiceCatalogEditor do catálogo real, o WhatsAppPairing com o hook de conexão — e grava
// nas mesmas APIs. O que é novo aqui é o roteiro de primeira vez: sugestões prontas de serviço,
// equipe com foto, o teste do bot ao vivo e a escolha/publicação do site.
//
// Regras do fluxo:
//  1. Avançar ou pular marca o passo no servidor (PUT /onboarding/steps/{id}): o dono pode parar
//     no celular e continuar no computador, e volta direto no primeiro passo que não viu.
//  2. Nenhum passo prende: "Pular por agora" sempre existe; o checklist do painel continua
//     cobrando o que ficou faltando de verdade (olha os dados, não este progresso).
//  3. Os saves da config do bot vão com ?welcome=false — o resumo de boas-vindas no WhatsApp do
//     dono continua sendo disparado pelo "Concluir" do modal de Configurações.
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router";
import { api, ApiError } from "../api/client";
import type { BotSettings, Professional, ServiceOffering, SiteTemplate } from "../api/types";
import { shortDate, type BotTestStatus, type OnboardingProgress, type OnboardingStepId } from "../api/onboarding";
import { RazorLogo } from "../components/Logo";
import { HoursFields, IdentityFields } from "../components/BotConfigFields";
import { ServiceCatalogEditor } from "../components/ServiceCatalogEditor";
import { WhatsAppPairing } from "../components/WhatsAppPairing";
import { AdminsEditor } from "../components/AdminsEditor";
import { Avatar } from "../components/Avatar";
import { SkeletonRows } from "../components/Skeleton";
import { useToast } from "../components/Toast";
import { useWhatsAppConnection } from "../lib/useWhatsAppConnection";
import { photoToAvatarDataUri } from "../lib/photo";
import { TEMPLATE_CATALOG } from "../site/catalog";
import "./onboarding.css";

interface StepDef {
  id: OnboardingStepId;
  title: string;
  hint: string;
  minutes: number;
}

const STEPS: StepDef[] = [
  { id: "casa", title: "Dados da casa", hint: "Como a barbearia se apresenta ao cliente — no bot e no site.", minutes: 1 },
  { id: "horario", title: "Horário de funcionamento", hint: "Os dias e horários que a IA pode oferecer na agenda.", minutes: 1 },
  { id: "servicos", title: "Serviços e preços", hint: "Já deixamos sugestões. Ajuste preço e duração do seu jeito.", minutes: 2 },
  { id: "equipe", title: "Equipe", hint: "Quem corta na casa. A IA agenda por barbeiro; a foto aparece no site.", minutes: 2 },
  { id: "whatsapp", title: "Conectar o WhatsApp", hint: "O número da barbearia — é ele que atende os clientes.", minutes: 2 },
  { id: "teste", title: "Testar o bot", hint: "Mande um “oi” de outro celular e veja a IA responder.", minutes: 1 },
  { id: "site", title: "Seu site", hint: "Escolha um modelo e publique. Dá para caprichar depois.", minutes: 1 },
];

/** Serviços sugeridos: o dono só ajusta preço e duração. */
const SUGGESTIONS: { name: string; price: number; durationMinutes: number }[] = [
  { name: "Corte de Cabelo", price: 40, durationMinutes: 30 },
  { name: "Barba", price: 30, durationMinutes: 30 },
  { name: "Corte + Barba (Combo)", price: 60, durationMinutes: 60 },
  { name: "Sobrancelha", price: 15, durationMinutes: 15 },
];

const ACCENTS = ["#3E63FF", "#C9A227", "#1F9D55", "#E4572E", "#8A63FF", "#111827"];

const norm = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().trim();

function errorMessage(e: unknown, fallback = "Erro ao salvar. Tente de novo."): string {
  return e instanceof ApiError ? e.message : fallback;
}

export function Onboarding() {
  const navigate = useNavigate();
  const toast = useToast();
  const [progress, setProgress] = useState<OnboardingProgress | null>(null);
  const [bot, setBot] = useState<BotSettings | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const originalName = useRef<string>("");

  useEffect(() => {
    let alive = true;
    Promise.all([
      api.get<OnboardingProgress>("/api/admin/onboarding"),
      api.get<BotSettings>("/api/admin/settings/bot"),
    ])
      .then(([p, b]) => {
        if (!alive) return;
        setProgress(p);
        setBot({ ...b, workingWeekdays: b.workingWeekdays ?? [] });
        originalName.current = b.displayName ?? "";
        // Retoma no primeiro passo que ainda não foi visto (concluído ou pulado).
        const first = STEPS.findIndex((s) => !p.stepsDone.includes(s.id));
        setIndex(first < 0 ? STEPS.length - 1 : first);
      })
      .catch((e) => alive && setLoadError(errorMessage(e, "Não consegui carregar sua configuração.")));
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    headingRef.current?.focus();
  }, [index]);

  const changeBot = (patch: Partial<BotSettings>) => {
    setDirty(true);
    setBot((b) => (b ? { ...b, ...patch } : b));
  };

  /** Grava o que o passo atual editou (só os passos que usam a config do bot). */
  const saveCurrent = async () => {
    if (!bot || !dirty) return;
    await api.put("/api/admin/settings/bot?welcome=false", bot);
    const name = (bot.displayName ?? "").trim();
    // O nome da barbearia (painel, site) acompanha o nome que o bot usa.
    if (name && name !== originalName.current) {
      await api.put("/api/admin/barbershop", { name });
      originalName.current = name;
    }
    setDirty(false);
  };

  const mark = async (id: OnboardingStepId) => {
    try {
      setProgress(await api.put<OnboardingProgress>(`/api/admin/onboarding/steps/${id}`));
    } catch {
      /* progresso é conveniência: falhar aqui não pode travar o dono */
    }
  };

  const goTo = async (target: number, markCurrent: boolean) => {
    if (busy) return;
    setBusy(true);
    try {
      await saveCurrent();
      if (markCurrent) await mark(STEPS[index].id);
      setIndex(Math.min(Math.max(target, 0), STEPS.length - 1));
      window.scrollTo({ top: 0 });
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const finish = async () => {
    setBusy(true);
    try {
      await saveCurrent();
      await mark(STEPS[index].id);
      await api.post("/api/admin/onboarding/complete");
      toast.success("Tudo pronto ✓ Sua barbearia está no ar.");
      navigate("/", { replace: true });
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    try {
      await saveCurrent();
    } catch (e) {
      toast.error(errorMessage(e));
      return;
    }
    navigate("/");
  };

  if (loadError) {
    return (
      <div className="ob">
        <div className="ob-center">
          <div className="error" role="alert">{loadError}</div>
          <button className="btn btn-ghost" onClick={() => navigate("/")}>Ir para o painel</button>
        </div>
      </div>
    );
  }
  if (!progress || !bot) {
    return (
      <div className="ob">
        <div className="ob-center"><SkeletonRows rows={4} /></div>
      </div>
    );
  }

  const step = STEPS[index];
  const isLast = index === STEPS.length - 1;
  const doneCount = STEPS.filter((s) => progress.stepsDone.includes(s.id)).length;
  const percent = Math.round((doneCount / STEPS.length) * 100);
  const minutesLeft = STEPS.slice(index).reduce((n, s) => n + s.minutes, 0);
  const canAdvance = step.id !== "casa" || (bot.displayName ?? "").trim() !== "";

  let body: ReactNode;
  switch (step.id) {
    case "casa":
      body = <IdentityFields draft={bot} onChange={changeBot} />;
      break;
    case "horario":
      body = <HoursFields draft={bot} onChange={changeBot} />;
      break;
    case "servicos":
      body = <ServicesStep />;
      break;
    case "equipe":
      body = <TeamStep />;
      break;
    case "whatsapp":
      body = <WhatsAppStep />;
      break;
    case "teste":
      body = <BotTestStep />;
      break;
    case "site":
      body = <SiteStep slug={progress.slug} />;
      break;
  }

  return (
    <div className="ob">
      <header className="ob-top">
        <RazorLogo size={22} />
        {progress.status === "TRIAL" && progress.trialEndsAt && (
          <span className="badge badge-plan ob-trial">Teste grátis até {shortDate(progress.trialEndsAt)}</span>
        )}
        <button className="btn btn-ghost btn-sm ob-leave" onClick={leave} disabled={busy}>
          Continuar depois
        </button>
      </header>
      <div
        className="ob-progress"
        role="progressbar"
        aria-label="Progresso da configuração guiada"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span style={{ width: `${Math.max(percent, 4)}%` }} />
      </div>

      <div className="ob-body">
        <nav className="ob-rail" aria-label="Etapas">
          <p className="ob-rail-head mono">CONFIGURAÇÃO GUIADA · ~{minutesLeft} MIN</p>
          <ol>
            {STEPS.map((s, i) => {
              const done = progress.stepsDone.includes(s.id);
              return (
                <li key={s.id}>
                  <button
                    type="button"
                    className={`ob-step${i === index ? " active" : ""}${done ? " done" : ""}`}
                    onClick={() => goTo(i, false)}
                    disabled={busy}
                    aria-current={i === index ? "step" : undefined}
                  >
                    <span className="ob-step-n mono" aria-hidden="true">{done ? "✓" : i + 1}</span>
                    <span className="ob-step-label">{s.title}</span>
                  </button>
                </li>
              );
            })}
          </ol>
        </nav>

        <main className="ob-pane">
          <p className="ob-count mono" aria-live="polite">
            PASSO {index + 1} DE {STEPS.length}
          </p>
          <h1 ref={headingRef} tabIndex={-1} className="ob-title">{step.title}</h1>
          <p className="ob-hint">{step.hint}</p>
          <div className="ob-card">{body}</div>

          <div className="ob-actions">
            <button className="btn btn-ghost" onClick={() => goTo(index - 1, false)} disabled={index === 0 || busy}>
              Voltar
            </button>
            <div className="ob-actions-right">
              {!isLast && (
                <button className="btn btn-ghost" onClick={() => goTo(index + 1, true)} disabled={busy}>
                  Pular por agora
                </button>
              )}
              <button
                className="btn btn-primary ob-next"
                onClick={() => (isLast ? finish() : goTo(index + 1, true))}
                disabled={busy || !canAdvance}
              >
                {busy ? "Salvando…" : isLast ? "Concluir" : "Continuar"}
              </button>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------- Serviços

function ServicesStep() {
  const toast = useToast();
  const [services, setServices] = useState<ServiceOffering[] | null>(null);
  const [version, setVersion] = useState(0);
  const [adding, setAdding] = useState<string | null>(null);

  const load = useCallback(() => {
    api.get<ServiceOffering[]>("/api/admin/services").then(setServices).catch(() => setServices([]));
  }, []);
  useEffect(load, [load, version]);

  const has = (name: string) => (services ?? []).some((s) => s.active && norm(s.name) === norm(name));
  const missing = SUGGESTIONS.filter((s) => !has(s.name));

  const add = async (s: (typeof SUGGESTIONS)[number]) => {
    setAdding(s.name);
    try {
      await api.post("/api/admin/services", s);
      setVersion((v) => v + 1); // remonta o editor com o serviço novo
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setAdding(null);
    }
  };

  return (
    <div className="stack" style={{ gap: 16 }}>
      {missing.length > 0 && (
        <div className="ob-suggest">
          <span className="faint">Sugestões:</span>
          {missing.map((s) => (
            <button key={s.name} type="button" className="ob-chip" onClick={() => add(s)} disabled={adding !== null}>
              + {s.name} <span className="mono">R$ {s.price}</span>
            </button>
          ))}
        </div>
      )}
      <ServiceCatalogEditor key={version} />
    </div>
  );
}

// ---------------------------------------------------------------------------------- Equipe

function TeamStep() {
  const toast = useToast();
  const [team, setTeam] = useState<Professional[] | null>(null);
  const [name, setName] = useState("");
  const [photo, setPhoto] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.get<Professional[]>("/api/admin/professionals").then(setTeam).catch(() => setTeam([]));
  }, []);
  useEffect(load, [load]);

  const pick = async (file: File | undefined): Promise<string | null> => {
    if (!file) return null;
    try {
      return await photoToAvatarDataUri(file);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Não consegui abrir a foto.");
      return null;
    }
  };

  const add = async () => {
    if (name.trim() === "") return toast.error("Digite o nome do barbeiro.");
    setBusy(true);
    try {
      await api.post("/api/admin/professionals", { name: name.trim(), photoUrl: photo ?? undefined });
      setName("");
      setPhoto(null);
      load();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const setPhotoOf = async (p: Professional, file: File | undefined) => {
    const uri = await pick(file);
    if (!uri) return;
    try {
      await api.put(`/api/admin/professionals/${p.id}`, {
        name: p.name,
        phone: p.phone,
        active: p.active,
        commissionPercent: p.commissionPercent,
        productCommissionPercent: p.productCommissionPercent,
        photoUrl: uri,
      });
      load();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  if (!team) return <SkeletonRows rows={2} />;
  const active = team.filter((p) => p.active);

  return (
    <div className="stack" style={{ gap: 14 }}>
      <ul className="ob-team">
        {active.map((p) => (
          <li key={p.id}>
            <Avatar name={p.displayName || p.name} photoUrl={p.photoUrl} size={44} />
            <span className="grow">{p.displayName || p.name}</span>
            <label className="btn btn-ghost btn-sm ob-file">
              {p.photoUrl ? "Trocar foto" : "Adicionar foto"}
              <input type="file" accept="image/*" onChange={(e) => setPhotoOf(p, e.target.files?.[0])} />
            </label>
          </li>
        ))}
      </ul>
      <div className="ob-add">
        <label className="ob-add-photo" title="Foto (opcional)">
          {photo ? <img src={photo} alt="Foto escolhida" /> : <span aria-hidden="true">＋</span>}
          <span className="sr-only">Foto do barbeiro (opcional)</span>
          <input type="file" accept="image/*" onChange={async (e) => setPhoto(await pick(e.target.files?.[0]))} />
        </label>
        <input
          className="input"
          placeholder="Nome do barbeiro"
          aria-label="Nome do barbeiro"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
        />
        <button type="button" className="btn btn-primary" onClick={add} disabled={busy}>
          {busy ? "…" : "Adicionar"}
        </button>
      </div>
      <p className="faint" style={{ margin: 0, fontSize: 13 }}>
        Comissão, apelido e quais serviços cada um faz ficam em <b>Equipe</b>, no painel.
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------------- WhatsApp

function WhatsAppStep() {
  const conn = useWhatsAppConnection();
  return (
    <div className="stack" style={{ gap: 18 }}>
      <WhatsAppPairing conn={conn} />
      <div className="ob-note">
        <b>Os dois números.</b> O número <b>do bot</b> é o da barbearia (o que você pareia acima). O{" "}
        <b>de admin</b> é o seu pessoal — já cadastramos o WhatsApp que você informou no cadastro. Eles
        precisam ser diferentes.
      </div>
      <details className="ob-details">
        <summary>Ver ou trocar o número de admin</summary>
        <AdminsEditor />
      </details>
    </div>
  );
}

// ---------------------------------------------------------------------------------- Teste do bot

const POLL_MS = 3000;

function BotTestStep() {
  const conn = useWhatsAppConnection();
  const [test, setTest] = useState<BotTestStatus | null>(null);

  useEffect(() => {
    let alive = true;
    const tick = () =>
      api
        .get<BotTestStatus>("/api/admin/onboarding/bot-test")
        .then((t) => alive && setTest(t))
        .catch(() => {});
    tick();
    const id = setInterval(tick, POLL_MS);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  const hhmm = (iso: string | null) => (iso ? iso.slice(11, 16) : "");
  const stage = !conn.connected ? 0 : !test?.received ? 1 : !test.replied ? 2 : 3;

  return (
    <div className="stack" style={{ gap: 18 }}>
      <ol className="ob-howto">
        <li>Pegue <b>outro celular</b> (o seu pessoal ou de alguém da equipe).</li>
        <li>Mande <b>“oi”</b> para o WhatsApp da barbearia.</li>
        <li>Peça um horário, como um cliente faria: <i>“tem horário amanhã à tarde?”</i></li>
      </ol>
      <ul className="ob-live" aria-live="polite">
        <li className={stage >= 1 ? "ok" : conn.loading ? "" : "warn"}>
          <span className="ob-dot" aria-hidden="true" />
          {conn.loading ? "Verificando o WhatsApp…" : conn.connected ? "WhatsApp conectado" : "WhatsApp ainda não conectado — volte um passo e leia o QR"}
        </li>
        <li className={stage >= 2 ? "ok" : stage === 1 ? "wait" : ""}>
          <span className="ob-dot" aria-hidden="true" />
          {stage >= 2 ? `Mensagem recebida às ${hhmm(test?.lastCustomerMessageAt ?? null)}` : "Esperando o seu “oi”…"}
        </li>
        <li className={stage >= 3 ? "ok" : stage === 2 ? "wait" : ""}>
          <span className="ob-dot" aria-hidden="true" />
          {stage >= 3 ? `O bot respondeu às ${hhmm(test?.lastBotMessageAt ?? null)} ✓` : "Resposta do bot"}
        </li>
      </ul>
      {stage === 3 && <p className="ob-win">Funcionou! Seus clientes já podem agendar pelo WhatsApp.</p>}
      <p className="faint" style={{ margin: 0, fontSize: 13 }}>
        Mandou do mesmo número que está conectado? Aí o WhatsApp trata como mensagem do próprio bot e ele
        não responde — use outro aparelho.
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------------- Site

type SiteRaw = Record<string, unknown> & { templateId?: SiteTemplate; accentColor?: string; published?: boolean };

function SiteStep({ slug }: { slug: string }) {
  const toast = useToast();
  const [site, setSite] = useState<SiteRaw | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<SiteRaw>("/api/admin/site").then(setSite).catch((e) => toast.error(errorMessage(e, "Erro ao carregar o site.")));
  }, [toast]);

  if (!site) return <SkeletonRows rows={3} />;
  const template = site.templateId ?? "CLASSICO";
  const accent = (site.accentColor ?? "#3E63FF").toUpperCase();
  const url = `${window.location.origin}/b/${slug}`;

  const save = async (patch: Partial<SiteRaw>, okMsg?: string) => {
    setBusy(true);
    try {
      // O PUT troca o registro inteiro: devolve tudo que veio do GET e só muda o que o passo mexe.
      const next = await api.put<SiteRaw>("/api/admin/site", { ...site, ...patch });
      setSite(next);
      if (okMsg) toast.success(okMsg);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div className="ob-templates" role="radiogroup" aria-label="Modelo do site">
        {TEMPLATE_CATALOG.map((t) => (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={template === t.id}
            className={`ob-tpl ob-tpl-${t.id.toLowerCase()}${template === t.id ? " active" : ""}`}
            style={{ ["--tpl-accent" as string]: accent }}
            onClick={() => save({ templateId: t.id })}
            disabled={busy}
          >
            <span className="ob-tpl-art" aria-hidden="true"><i /><i /><i /></span>
            <b>{t.label}</b>
            <span>{t.pitch}</span>
          </button>
        ))}
      </div>
      <div className="row" style={{ gap: 8, flexWrap: "wrap" }} role="radiogroup" aria-label="Cor de destaque">
        <span className="faint" style={{ fontSize: 13 }}>Cor:</span>
        {ACCENTS.map((c) => (
          <button
            key={c}
            type="button"
            role="radio"
            aria-checked={accent === c}
            aria-label={`Cor ${c}`}
            className={`ob-swatch${accent === c ? " active" : ""}`}
            style={{ background: c }}
            onClick={() => save({ accentColor: c })}
            disabled={busy}
          />
        ))}
      </div>
      <div className="ob-publish">
        {site.published ? (
          <>
            <span className="badge badge-green">● No ar</span>
            <a href={url} target="_blank" rel="noreferrer" className="mono ob-url">{url.replace(/^https?:\/\//, "")}</a>
          </>
        ) : (
          <>
            <span className="faint" style={{ fontSize: 13.5 }}>Seu endereço: <span className="mono">/b/{slug}</span></span>
            <button type="button" className="btn btn-primary" disabled={busy}
              onClick={() => save({ published: true, onlineBookingEnabled: true }, "Site publicado ✓")}>
              Publicar meu site
            </button>
          </>
        )}
      </div>
      <p className="faint" style={{ margin: 0, fontSize: 13 }}>
        Textos, fotos e seções ficam em <b>Configurações → Site</b>. Trocar de modelo não perde nada.
      </p>
    </div>
  );
}
