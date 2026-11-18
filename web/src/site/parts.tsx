// Blocos que os três templates compartilham.
//
// Serviços, equipe, planos e rodapé mostram o MESMO dado nos três — o que muda entre templates é
// a composição da página, não o conteúdo. Duplicar estes blocos faria uma correção de preço ou de
// acessibilidade precisar ser feita três vezes, e a terceira seria esquecida.
import { Link } from "react-router-dom";
import type { PublicSite } from "../api/types";
import { bookLabel, section } from "./content";
import { useTilt } from "./fx";

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
export const money = (v: number) => BRL.format(v);

/** Conversa no WhatsApp da barbearia, já com a mensagem de agendamento. */
export function whatsappHref(site: Pick<PublicSite, "whatsappNumber" | "barbershopName">): string | null {
  if (!site.whatsappNumber) return null;
  const digits = site.whatsappNumber.replace(/\D/g, "");
  if (!digits) return null;
  const msg = encodeURIComponent(`Olá! Quero agendar um horário na ${site.barbershopName}.`);
  return `https://wa.me/${digits}?text=${msg}`;
}

/** Caminho da página de agendamento online (rota do SPA, mesma origem). */
export const onlineBookingPath = (slug: string) => `/b/${encodeURIComponent(slug)}/agendar`;

/**
 * Para onde o "Agendar" leva — a porta de entrada do produto. Com o agendamento online ligado,
 * a página própria (o cliente vê os horários livres e marca sozinho); senão, o WhatsApp.
 */
export function bookingHref(site: PublicSite): string | null {
  if (site.onlineBooking) return onlineBookingPath(site.slug);
  return whatsappHref(site);
}

export function BookButton({ site, ghost }: { site: PublicSite; ghost?: boolean }) {
  const cls = `rz-btn ${ghost ? "rz-btn-ghost" : "rz-btn-primary"}`;
  const label = bookLabel(site);
  const inner = (
    <>
      <span className="rz-btn-label">{label}</span>
      <svg className="rz-btn-arrow" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path d="M5 12h13M13 6l6 6-6 6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </>
  );
  if (site.onlineBooking) {
    // Link do router: navegação interna, sem recarregar o app nem abrir aba.
    return (
      <Link className={cls} to={onlineBookingPath(site.slug)}>
        {inner}
      </Link>
    );
  }
  const href = whatsappHref(site);
  // Sem WhatsApp configurado o botão não aparece: um "Agendar" que não leva a lugar nenhum é
  // pior que a ausência dele.
  if (!href) return null;
  return (
    <a className={cls} href={href} target="_blank" rel="noopener noreferrer">
      {inner}
    </a>
  );
}

/**
 * Cada template mostra a equipe do seu jeito (CSS `.rz-team-*` no fim de site.css): o Clássico
 * em cartões compactos, o Estúdio em retrato grande (a equipe é o destaque dele) e a Vitrine com a
 * foto de ponta a ponta (lá a imagem é o argumento). O dado é o mesmo nos três.
 */
const TEAM_VARIANT: Record<PublicSite["template"], "cards" | "portrait" | "tiles"> = {
  CLASSICO: "cards",
  ESTUDIO: "portrait",
  VITRINE: "tiles",
};

/** Defesa extra além do backend: só data URI de imagem vira <img>, só http(s) vira link. */
const isPhoto = (u: string | null) => !!u && /^data:image\/(jpeg|png|webp);base64,/.test(u);
const isHttp = (u: string | null) => !!u && /^https?:\/\//i.test(u);

function igHandle(url: string): string {
  const m = url.match(/instagram\.com\/([A-Za-z0-9._]+)/i);
  return m ? `@${m[1]}` : "Instagram";
}


/** Cabeçalho simples dos blocos compartilhados (o de sections.tsx tem numeração e ornamentos). */
function Head({ eyebrow, title, text, id }: { eyebrow: string; title: string; text: string; id: string }) {
  if (!eyebrow && !title && !text) return null;
  return (
    <div className="rz-head" data-reveal>
      {eyebrow && <p className="rz-eyebrow">{eyebrow}</p>}
      {title && (
        <h2 className="rz-h2" id={id}>
          {title}
        </h2>
      )}
      {text && <p className="rz-lead rz-head-text">{text}</p>}
    </div>
  );
}

const labelledBy = (title: string, id: string) => (title ? { "aria-labelledby": id } : {});

export function Team({ site }: { site: PublicSite }) {
  const tilt = useTilt<HTMLDivElement>();
  const s = section(site, "team");
  if (site.team.length === 0 || !s.visible) return null;
  const variant = TEAM_VARIANT[site.template] ?? "cards";
  return (
    <section className="rz-section rz-section-alt rz-team-sec" id="equipe" {...labelledBy(s.title, "rz-equipe")}>
      <div className="rz-wrap">
        <Head eyebrow={s.eyebrow} title={s.title} text={s.text} id="rz-equipe" />
        <div className={`rz-team rz-team-${variant}`} ref={tilt}>
          {site.team.map((m, i) => {
            // O apelido é como a casa chama o barbeiro — é o nome que o cliente vai pedir.
            const shown = m.nickname || m.name;
            return (
              <article
                className="rz-team-member"
                key={`${m.name}-${i}`}
                data-reveal
                data-tilt={variant === "tiles" ? "" : undefined}
              >
                <div className="rz-team-pic">
                  {isPhoto(m.photoUrl) ? (
                    <img className="rz-team-photo" src={m.photoUrl!} alt={`Foto de ${shown}`} loading="lazy" />
                  ) : (
                    // Sem foto: a inicial em monograma — melhor que um avatar genérico igual
                    // para todo mundo.
                    <div className="rz-team-initial" aria-hidden="true">
                      {shown.charAt(0).toUpperCase()}
                    </div>
                  )}
                </div>
                <div className="rz-team-info">
                  <h3 className="rz-team-name">{shown}</h3>
                  {m.nickname && m.nickname !== m.name && <div className="rz-team-fullname">{m.name}</div>}
                  {m.bio && <p className="rz-team-bio">{m.bio}</p>}
                  {m.specialties.length > 0 && (
                    <ul className="rz-team-tags" aria-label={`Especialidades de ${shown}`}>
                      {m.specialties.map((t) => (
                        <li key={t}>{t}</li>
                      ))}
                    </ul>
                  )}
                  {isHttp(m.instagramUrl) && (
                    <a className="rz-team-ig" href={m.instagramUrl!} target="_blank" rel="noopener noreferrer">
                      {igHandle(m.instagramUrl!)}
                    </a>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

export function Plans({ site }: { site: PublicSite }) {
  const s = section(site, "plans");
  if (site.plans.length === 0 || !s.visible) return null;
  // Com três ou mais, o do meio é o destacado — a convenção que o visitante já lê como "o recomendado".
  const featured = site.plans.length >= 3 ? 1 : -1;
  return (
    <section className="rz-section rz-plans-sec" id="planos" {...labelledBy(s.title, "rz-planos")}>
      <div className="rz-wrap">
        <Head eyebrow={s.eyebrow} title={s.title} text={s.text} id="rz-planos" />
        <div className="rz-plans">
          {site.plans.map((p, i) => (
            <div className={`rz-plan ${i === featured ? "is-featured" : ""}`} key={p.name} data-reveal>
              <strong className="rz-plan-name">{p.name}</strong>
              <div className="rz-plan-price">
                {money(p.price)} <small>/mês</small>
              </div>
              {p.description && <p className="rz-plan-desc">{p.description}</p>}
              <div className="rz-plan-cta">
                <BookButton site={site} ghost={i !== featured} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Endereço do bot sem marcação do WhatsApp e sem o emoji de pino no começo das linhas. */
const cleanAddress = (t: string) =>
  t
    .replace(/[*_~]/g, "")
    .split(/\n+/)
    .map((l) => l.replace(/^[^\p{L}\p{N}]+/u, "").trim())
    .filter(Boolean)
    .join("\n");

export function Footer({ site }: { site: PublicSite }) {
  const wa = whatsappHref(site);
  const mapa = isHttp(site.mapsUrl) ? site.mapsUrl : null;
  const insta = isHttp(site.instagramUrl) ? site.instagramUrl : null;
  const ano = new Date().getFullYear();
  return (
    <footer className="rz-foot">
      <div className="rz-wrap">
        <p className="rz-foot-mark" aria-hidden="true" style={{ ["--chars" as string]: Math.max(6, site.barbershopName.length) }}>
          {site.barbershopName}
        </p>
        <div className="rz-foot-grid">
          <div className="rz-foot-col">
            <strong className="rz-foot-name">{site.barbershopName}</strong>
            {site.addressText && (
              // O endereço vem do bot, escrito com quebras de linha e marcação do WhatsApp.
              <p className="rz-foot-addr">{cleanAddress(site.addressText)}</p>
            )}
          </div>
          {site.openingHours && (
            <div className="rz-foot-col">
              <small>Horário</small>
              <p>{site.openingHours}</p>
            </div>
          )}
          {(mapa || insta || wa) && (
            <div className="rz-foot-col">
              <small>Contato</small>
              <ul className="rz-foot-links">
                {mapa && (
                  <li>
                    <a href={mapa} target="_blank" rel="noopener noreferrer">
                      Como chegar
                    </a>
                  </li>
                )}
                {insta && (
                  <li>
                    <a href={insta} target="_blank" rel="noopener noreferrer">
                      Instagram
                    </a>
                  </li>
                )}
                {/* WhatsApp continua como canal secundário mesmo com o agendamento online ligado. */}
                {wa && (
                  <li>
                    <a href={wa} target="_blank" rel="noopener noreferrer">
                      WhatsApp
                    </a>
                  </li>
                )}
              </ul>
            </div>
          )}
        </div>
        <div className="rz-foot-bar">
          <span>
            © {ano} {site.barbershopName}
          </span>
          <a href="#topo" className="rz-foot-top">
            Voltar ao topo <span aria-hidden="true">↑</span>
          </a>
        </div>
      </div>
    </footer>
  );
}

/**
 * Faixa de números logo abaixo do hero.
 *
 * Tudo aqui é DERIVADO do que a barbearia realmente cadastrou — nada de "mais de 1000 clientes
 * satisfeitos". Número inventado num site é o mesmo problema que o painel já resolveu ao remover
 * os dados fabricados: some a confiança quando alguém confere.
 */
export function Stats({ site }: { site: PublicSite }) {
  const s = section(site, "stats");
  if (!s.visible) return null;
  const itens: { v: string; l: string }[] = [];
  if (site.team.length > 0) {
    itens.push({ v: String(site.team.length), l: site.team.length === 1 ? "barbeiro" : "barbeiros na equipe" });
  }
  if (site.services.length > 0) {
    itens.push({ v: String(site.services.length), l: "serviços no cardápio" });
  }
  const comPreco = site.services.filter((sv) => sv.price != null);
  if (comPreco.length > 0) {
    itens.push({ v: money(Math.min(...comPreco.map((sv) => sv.price!))), l: "a partir de" });
  }
  if (site.plans.length > 0) {
    itens.push({ v: money(Math.min(...site.plans.map((p) => p.price))), l: "clube por mês" });
  }
  if (itens.length < 2) return null;

  return (
    <div className="rz-stats" data-reveal>
      <div className="rz-wrap rz-stats-in">
        {itens.map((i) => (
          <div className="rz-stat" key={i.l}>
            <div className="rz-stat-v">{i.v}</div>
            <div className="rz-stat-l">{i.l}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Os três passos do agendamento por WhatsApp — descrevem o produto, não a barbearia. */
const STEPS = [
  { t: "Chama no WhatsApp", d: "Sem baixar app e sem criar conta. É a mesma conversa que você já usa todo dia." },
  { t: "Escolhe o horário", d: "Respondemos na hora com os horários livres — pode mandar áudio se preferir." },
  { t: "Pronto", d: "Você recebe a confirmação e um lembrete uma hora antes. Só chegar." },
];

/**
 * "Como funciona" em três passos.
 *
 * Existe porque agendar por WhatsApp com um robô é novo para muita gente: sem explicar, o
 * visitante hesita justamente no clique que o site inteiro persegue.
 */
export function HowItWorks({ site }: { site: PublicSite }) {
  const s = section(site, "how");
  // O passo a passo descreve o WhatsApp; com agendamento online a própria página já guia.
  if (site.onlineBooking || !whatsappHref(site) || !s.visible) return null;
  return (
    <section className="rz-section rz-section-alt" {...labelledBy(s.title, "rz-como")}>
      <div className="rz-wrap">
        <Head eyebrow={s.eyebrow} title={s.title} text={s.text} id="rz-como" />
        <ol className="rz-steps">
          {STEPS.map((st) => (
            <li className="rz-step" key={st.t} data-reveal>
              <h3>{st.t}</h3>
              <p>{st.d}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

/** Barra fixa de agendamento no celular — o botão do topo some ao rolar. */
export function StickyBook({ site }: { site: PublicSite }) {
  if (!bookingHref(site)) return null;
  return (
    <div className="rz-sticky">
      <BookButton site={site} />
    </div>
  );
}
