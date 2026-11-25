// Seções com VARIANTE por template — o que dá a cada modelo uma identidade própria.
//
// parts.tsx guarda os blocos que são iguais nos três (equipe, planos, rodapé). Aqui ficam os que
// mudam de forma: o menu de serviços vira quadro de barbearia no Clássico, carta editorial no
// Estúdio e cartões com foto na Vitrine. O DADO continua o mesmo — trocar de template nunca pede
// para redigitar nada.
//
// Texto de seção NUNCA fica fixo aqui: olho, título e apoio vêm de `section(site, chave)` em
// content.ts (padrão do template + o que o dono escreveu), e a seção some com `.visible === false`.
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import type { PublicSite } from "../api/types";
import { BookButton, bookingHref, money } from "./parts";
import { useFocusTrap } from "../lib/useFocusTrap";
import { section, type ResolvedSection } from "./content";
import { Magnetic, ScrollMeter, SplitWords, useMotion, useReveal, useScrollVar, useTilt } from "./fx";
import { BarberPole, Comb, PhotoRing, Razor, Scissors } from "./ornaments";

export type TemplateKey = "classico" | "estudio" | "vitrine";

/** Só http(s): um `javascript:` vindo do cadastro nunca vira link clicável no site. */
export function safeUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const u = new URL(url, window.location.origin);
    return u.protocol === "https:" || u.protocol === "http:" ? u.href : null;
  } catch {
    return null;
  }
}

const photosOf = (site: PublicSite) => site.gallery.map((g) => safeUrl(g)).filter(Boolean) as string[];

/* ---------------------------------------------------------------------------
   Casca do template: fontes próprias, revelação ao rolar, fio de leitura.
   ------------------------------------------------------------------------- */

const FONTS: Record<TemplateKey, string> = {
  classico:
    "family=Playfair+Display:ital,wght@0,500;0,600;0,700;0,800;1,500;1,600&family=Inter:wght@400;500;600;700",
  estudio: "family=Fraunces:ital,opsz,wght@0,9..144,300;0,9..144,400;0,9..144,600;1,9..144,300&family=Inter:wght@300;400;500;600",
  vitrine: "family=Anton&family=Inter:wght@400;500;600;700;800",
};

function useTemplateFonts(key: TemplateKey) {
  useEffect(() => {
    const id = "rz-site-fonts";
    let link = document.getElementById(id) as HTMLLinkElement | null;
    if (!link) {
      link = document.createElement("link");
      link.id = id;
      link.rel = "stylesheet";
      document.head.appendChild(link);
    }
    link.href = `https://fonts.googleapis.com/css2?${FONTS[key]}&display=swap`;
  }, [key]);
}

export function TemplateShell({ tpl, children }: { tpl: TemplateKey; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const motion = useMotion();
  useTemplateFonts(tpl);
  useReveal(ref, motion);
  return (
    <div ref={ref} className={`tpl tpl-${tpl}`}>
      <ScrollMeter />
      {children}
    </div>
  );
}

/* ---------------------------------------------------------------------------
   Cabeçalho de seção: olho, título e texto de apoio — os três editáveis pelo dono.
   ------------------------------------------------------------------------- */

export function SectionHead({
  s,
  id,
  center,
  index,
  reveal = true,
  children,
}: {
  s: ResolvedSection;
  id?: string;
  center?: boolean;
  /** Numeração editorial ("01") — só o Estúdio usa. */
  index?: number;
  /** false quando o pai já se revela inteiro (evita animar duas vezes). */
  reveal?: boolean;
  children?: ReactNode;
}) {
  if (!s.eyebrow && !s.title && !s.text && !children) return null;
  return (
    <div className={`rz-head ${center ? "rz-head-center" : ""}`} data-reveal={reveal ? "" : undefined}>
      {(s.eyebrow || index != null) && (
        <p className="rz-eyebrow">
          {index != null && <span className="rz-eyebrow-n">{String(index).padStart(2, "0")}</span>}
          {s.eyebrow}
        </p>
      )}
      {children}
      {s.title && (
        <h2 className="rz-h2" id={id}>
          {s.title}
        </h2>
      )}
      {s.text && <p className="rz-lead rz-head-text">{s.text}</p>}
    </div>
  );
}

/** aria-labelledby só quando o título existe — rótulo apontando para o nada é pior que nenhum. */
const labelled = (s: ResolvedSection, id: string) => (s.title ? { "aria-labelledby": id } : {});

/* ---------------------------------------------------------------------------
   Navegação fixa: transparente sobre a capa, sólida ao rolar.
   ------------------------------------------------------------------------- */

export function NavBar({ site, solid }: { site: PublicSite; solid?: boolean }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 40);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, []);

  const on = (k: Parameters<typeof section>[1]) => section(site, k).visible;
  // Só aparecem âncoras para seções que existem — link para bloco vazio é link quebrado.
  const links: { href: string; label: string }[] = [];
  if (site.services.length && on("services")) links.push({ href: "#servicos", label: "Serviços" });
  if (site.gallery.length && on("gallery")) links.push({ href: "#trabalho", label: "Trabalhos" });
  if (site.team.length && on("team")) links.push({ href: "#equipe", label: "Equipe" });
  if (site.plans.length && on("plans")) links.push({ href: "#planos", label: "Clube" });
  if ((site.addressText || site.mapsUrl || site.openingHours) && on("location")) links.push({ href: "#local", label: "Onde estamos" });

  const logo = safeUrl(site.logoUrl);
  const isSolid = scrolled || solid || !on("hero");
  return (
    <header className={`rz-nav ${isSolid ? "is-solid" : ""}`}>
      <div className="rz-wrap rz-nav-in">
        <a href="#topo" className="rz-nav-brand">
          {logo ? <img src={logo} alt={site.barbershopName} /> : <span>{site.barbershopName}</span>}
        </a>
        <nav aria-label="Seções" className="rz-nav-links">
          {links.map((l) => (
            <a key={l.href} href={l.href}>
              {l.label}
            </a>
          ))}
        </nav>
        <div className="rz-nav-cta">
          <BookButton site={site} />
        </div>
      </div>
    </header>
  );
}

/* ---------------------------------------------------------------------------
   Capas.
   ------------------------------------------------------------------------- */

/** Clássico: texto à esquerda; à direita, foto em arco com moldura dupla e o poste girando. */
export function HeroSplit({ site }: { site: PublicSite }) {
  const motion = useMotion();
  const ref = useScrollVar<HTMLElement>(motion === "full");
  const s = section(site, "hero");
  if (!s.visible) return <div id="topo" className="rz-hero-gap" />;
  const foto = safeUrl(site.heroImageUrl) ?? photosOf(site)[0] ?? null;
  const mapa = safeUrl(site.mapsUrl);
  const titulo = site.headline || site.barbershopName;
  return (
    <section className="rz-hsplit" id="topo" ref={ref}>
      <div className="rz-wrap rz-hsplit-in">
        <div className="rz-hsplit-text">
          {s.eyebrow && (
            <p className="rz-eyebrow rz-rise" style={{ ["--d" as string]: 0 }}>
              {s.eyebrow}
            </p>
          )}
          <h1 className="rz-split">
            <SplitWords text={titulo} delay={1} />
          </h1>
          {site.subheadline && (
            <p className="rz-lead rz-rise" style={{ ["--d" as string]: 4 }}>
              {site.subheadline}
            </p>
          )}
          <div className="rz-hero-cta rz-rise" style={{ ["--d" as string]: 5 }}>
            <Magnetic>
              <BookButton site={site} />
            </Magnetic>
            {mapa && (
              <a className="rz-btn rz-btn-ghost" href={mapa} target="_blank" rel="noopener noreferrer">
                Como chegar
              </a>
            )}
          </div>
          {(site.openingHours || site.addressText) && (
            <dl className="rz-hsplit-facts rz-rise" style={{ ["--d" as string]: 6 }}>
              {site.openingHours && (
                <div>
                  <dt>Horário</dt>
                  <dd>{site.openingHours}</dd>
                </div>
              )}
              {site.addressText && (
                <div>
                  <dt>Endereço</dt>
                  <dd>{firstLines(site.addressText, 2)}</dd>
                </div>
              )}
            </dl>
          )}
        </div>
        <div className="rz-hsplit-media rz-rise" style={{ ["--d" as string]: 2 }}>
          <div className="rz-arch">
            {foto ? <img src={foto} alt="" loading="eager" /> : <div className="rz-arch-ph" aria-hidden="true" />}
          </div>
          <div className="rz-hsplit-pole">
            <BarberPole />
          </div>
        </div>
      </div>
    </section>
  );
}

/** Estúdio: foto P&B em tela cheia, título gigante e a navalha 3D que abre com a rolagem. */
export function HeroEditorial({ site }: { site: PublicSite }) {
  const motion = useMotion();
  const ref = useScrollVar<HTMLElement>(motion === "full");
  const s = section(site, "hero");
  if (!s.visible) return <div id="topo" className="rz-hero-gap" />;
  const foto = safeUrl(site.heroImageUrl) ?? photosOf(site)[0] ?? null;
  const titulo = site.headline || site.barbershopName;
  return (
    <section className="rz-hed" id="topo" ref={ref}>
      {foto && (
        <div className="rz-hed-bg" aria-hidden="true">
          <img src={foto} alt="" loading="eager" />
        </div>
      )}
      <div className="rz-hed-veil" aria-hidden="true" />
      <div className="rz-wrap rz-hed-in">
        <div className="rz-hed-copy">
          {s.eyebrow && (
            <p className="rz-hed-kicker rz-rise" style={{ ["--d" as string]: 0 }}>
              {s.eyebrow}
            </p>
          )}
          <h1 className="rz-split">
            <SplitWords text={titulo} delay={1} />
          </h1>
          {site.subheadline && (
            <p className="rz-hed-sub rz-rise" style={{ ["--d" as string]: 5 }}>
              {site.subheadline}
            </p>
          )}
          <div className="rz-hero-cta rz-rise" style={{ ["--d" as string]: 6 }}>
            <Magnetic>
              <BookButton site={site} />
            </Magnetic>
          </div>
        </div>
        <div className="rz-hed-razor">
          <Razor engraving={site.barbershopName} />
        </div>
      </div>
      <div className="rz-hed-foot rz-wrap" aria-hidden={site.openingHours ? undefined : true}>
        {site.openingHours && <p className="rz-hed-hours">{site.openingHours}</p>}
        <a href="#casa" className="rz-hed-scroll" aria-label="Rolar para o conteúdo">
          <span />
        </a>
      </div>
    </section>
  );
}

/** Vitrine: título em cartaz, anel 3D com as fotos e o selo girando com o texto do botão. */
export function HeroPoster({ site, bookLabel }: { site: PublicSite; bookLabel: string }) {
  const motion = useMotion();
  const ref = useScrollVar<HTMLElement>(motion === "full");
  const s = section(site, "hero");
  if (!s.visible) return <div id="topo" className="rz-hero-gap" />;
  const fotos = photosOf(site);
  const anel = fotos.length >= 4 ? fotos.slice(0, 8) : null;
  const titulo = site.headline || site.barbershopName;
  return (
    <section className="rz-poster" id="topo" ref={ref}>
      <div className="rz-wrap rz-poster-in">
        <div className="rz-poster-copy">
          {s.eyebrow && (
            <p className="rz-chip rz-rise" style={{ ["--d" as string]: 0 }}>
              <span className="rz-chip-dot" aria-hidden="true" />
              {s.eyebrow}
            </p>
          )}
          <h1 className="rz-split">
            <SplitWords text={titulo} delay={1} />
          </h1>
          <div className="rz-poster-row rz-rise" style={{ ["--d" as string]: 5 }}>
            {site.subheadline && <p className="rz-lead">{site.subheadline}</p>}
            <Magnetic>
              <BookButton site={site} />
            </Magnetic>
          </div>
        </div>
        <div className="rz-poster-media rz-rise" style={{ ["--d" as string]: 3 }}>
          {anel ? (
            <PhotoRing photos={anel} />
          ) : (
            fotos[0] && (
              <div className="rz-poster-single">
                <img src={fotos[0]} alt="" />
              </div>
            )
          )}
          <Sticker label={bookLabel} />
        </div>
      </div>
    </section>
  );
}

/** Selo redondo com texto girando (Vitrine) — o mesmo texto do botão de agendar. */
function Sticker({ label }: { label: string }) {
  const text = `${label} ✦ ${label} ✦ `;
  return (
    <div className="rz-sticker" aria-hidden="true">
      <svg viewBox="0 0 120 120">
        <defs>
          <path id="rz-sticker-path" d="M60 60m-44 0a44 44 0 1 1 88 0a44 44 0 1 1 -88 0" />
        </defs>
        <circle cx="60" cy="60" r="58" className="rz-sticker-bg" />
        <text className="rz-sticker-text">
          <textPath href="#rz-sticker-path" textLength="272" lengthAdjust="spacingAndGlyphs">
            {text}
          </textPath>
        </text>
      </svg>
      <span className="rz-sticker-mark">✦</span>
    </div>
  );
}

/* ---------------------------------------------------------------------------
   Faixas decorativas.
   ------------------------------------------------------------------------- */

/** Faixa rolando com os serviços (Vitrine) — some junto com a seção de serviços. */
export function Marquee({ site }: { site: PublicSite }) {
  const itens = site.services.map((s) => s.name);
  if (itens.length === 0 || !section(site, "services").visible) return null;
  const linha = [...itens, ...itens, ...itens];
  return (
    <div className="rz-marquee" aria-hidden="true">
      <div className="rz-marquee-track">
        {[0, 1].map((k) => (
          <span key={k}>
            {linha.map((t, i) => (
              <span key={i}>
                {t} <b>✦</b>{" "}
              </span>
            ))}
          </span>
        ))}
      </div>
    </div>
  );
}

/** Listras de poste de barbeiro — o símbolo mais reconhecível do ofício. */
export function BarberStripe() {
  return <div className="rz-stripe" aria-hidden="true" />;
}

/* ---------------------------------------------------------------------------
   Serviços em três formas.
   ------------------------------------------------------------------------- */

/** Clássico: quadro de preços em moldura dupla, com tesoura e pente desenhados a traço. */
export function ServicesBoard({ site }: { site: PublicSite }) {
  const s = section(site, "services");
  if (site.services.length === 0 || !s.visible) return null;
  return (
    <section className="rz-section rz-services-board" {...labelled(s, "servicos")}>
      <div className="rz-wrap">
        <SectionHead s={s} id="servicos" center>
          <div className="rz-ornament" aria-hidden="true">
            <Scissors />
            <span className="rz-ornament-rule" />
            <Comb />
          </div>
        </SectionHead>
        <div className="rz-board" data-reveal>
          {site.services.map((sv) => (
            <div className="rz-board-row" key={sv.name}>
              <div className="rz-board-line">
                <span className="rz-board-name">{sv.name}</span>
                <span className="rz-board-dots" />
                {sv.price != null && <span className="rz-board-price">{money(sv.price)}</span>}
              </div>
              {sv.durationMinutes != null && <span className="rz-board-dur">{sv.durationMinutes} minutos</span>}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Estúdio: carta numerada, tipografia grande e linhas finas — cardápio de restaurante caro. */
export function ServicesEditorial({ site, index }: { site: PublicSite; index?: number }) {
  const s = section(site, "services");
  if (site.services.length === 0 || !s.visible) return null;
  return (
    <section className="rz-section" {...labelled(s, "servicos")}>
      <div className="rz-wrap rz-ed-grid">
        <SectionHead s={s} id="servicos" index={index} />
        <ol className="rz-ed-list">
          {site.services.map((sv, i) => (
            <li key={sv.name} data-reveal>
              <span className="rz-ed-n">{String(i + 1).padStart(2, "0")}</span>
              <span className="rz-ed-name">{sv.name}</span>
              {sv.durationMinutes != null && <span className="rz-ed-dur">{sv.durationMinutes} min</span>}
              {sv.price != null && <span className="rz-ed-price">{money(sv.price)}</span>}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

/** Vitrine: cartões com foto do trabalho ao fundo, inclinando com o ponteiro. */
export function ServicesCards({ site }: { site: PublicSite }) {
  const tilt = useTilt<HTMLDivElement>();
  const s = section(site, "services");
  if (site.services.length === 0 || !s.visible) return null;
  const fotos = photosOf(site);
  return (
    <section className="rz-section" {...labelled(s, "servicos")}>
      <div className="rz-wrap">
        <SectionHead s={s} id="servicos" />
        <div className="rz-scards" ref={tilt}>
          {site.services.map((sv, i) => {
            const f = fotos.length ? fotos[(i + 1) % fotos.length] : null;
            return (
              <article className="rz-scard" key={sv.name} data-reveal data-tilt>
                <div className="rz-scard-in">
                  {f && <div className="rz-scard-bg" style={{ backgroundImage: `url("${f}")` }} aria-hidden="true" />}
                  <span className="rz-scard-n" aria-hidden="true">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <div className="rz-scard-body">
                    <h3>{sv.name}</h3>
                    <div className="rz-scard-meta">
                      {sv.durationMinutes != null && <span>{sv.durationMinutes} min</span>}
                      {sv.price != null && <strong>{money(sv.price)}</strong>}
                    </div>
                  </div>
                  <span className="rz-glare" aria-hidden="true" />
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* ---------------------------------------------------------------------------
   Galeria com ampliação.
   ------------------------------------------------------------------------- */

type GalleryVariant = "frames" | "carousel" | "masonry";

export function GalleryPro({ site, variant, index }: { site: PublicSite; variant: GalleryVariant; index?: number }) {
  const s = section(site, "gallery");
  const todas = photosOf(site).slice(0, 12);
  // Molduras são grade de 4: só linhas completas (a última com 2 fotos parece site inacabado).
  const fotos = variant === "frames" && todas.length > 4 ? todas.slice(0, todas.length - (todas.length % 4)) : todas;
  const [aberta, setAberta] = useState<number | null>(null);
  const fechar = useCallback(() => setAberta(null), []);
  const tilt = useTilt<HTMLDivElement>();
  if (fotos.length === 0 || !s.visible) return null;
  const insta = safeUrl(site.instagramUrl);

  const item = (src: string, i: number) => (
    <button
      type="button"
      key={src}
      className="rz-gal-item"
      onClick={() => setAberta(i)}
      aria-label={`Ampliar foto ${i + 1} de ${fotos.length}`}
      data-reveal={variant === "carousel" ? undefined : ""}
      data-tilt={variant === "masonry" ? "" : undefined}
    >
      <img src={src} alt={`Trabalho ${i + 1} da ${site.barbershopName}`} loading={i < 3 ? "eager" : "lazy"} draggable={false} />
      {variant === "carousel" && <span className="rz-gal-cap">{String(i + 1).padStart(2, "0")}</span>}
    </button>
  );

  return (
    <section className={`rz-section rz-gal rz-gal-${variant}`} {...labelled(s, "trabalho")}>
      <div className="rz-wrap">
        <SectionHead s={s} id="trabalho" index={index} />
      </div>
      {variant === "carousel" ? (
        <Carousel count={fotos.length}>{fotos.map(item)}</Carousel>
      ) : (
        <div className="rz-wrap">
          <div className="rz-gal-grid" ref={tilt}>
            {fotos.map(item)}
          </div>
        </div>
      )}
      {insta && (
        <div className="rz-wrap rz-gal-more" data-reveal>
          <a className="rz-btn rz-btn-ghost" href={insta} target="_blank" rel="noopener noreferrer">
            Ver mais no Instagram
          </a>
        </div>
      )}
      {aberta != null && <Lightbox fotos={fotos} index={aberta} onIndex={setAberta} onClose={fechar} />}
    </section>
  );
}

/**
 * Carrossel sem barra de rolagem: arrasta com o dedo (rolagem nativa + snap), com o mouse
 * (arrastar) ou pelas setas. O progresso é um fio fino — a barra cinza do navegador não aparece.
 */
function Carousel({ count, children }: { count: number; children: ReactNode }) {
  const track = useRef<HTMLDivElement>(null);
  const thumb = useRef<HTMLSpanElement>(null);
  const [pos, setPos] = useState({ i: 0, start: true, end: count <= 1 });
  const drag = useRef<{ x: number; left: number; moved: boolean } | null>(null);

  useEffect(() => {
    const el = track.current;
    if (!el) return;
    let raf = 0;
    const update = () => {
      raf = 0;
      const max = el.scrollWidth - el.clientWidth;
      const ratio = el.clientWidth / Math.max(1, el.scrollWidth);
      if (thumb.current) {
        thumb.current.style.width = `${Math.max(8, ratio * 100)}%`;
        thumb.current.style.transform = `translateX(${(el.scrollLeft / Math.max(1, el.clientWidth)) * 100}%)`;
      }
      const first = el.firstElementChild as HTMLElement | null;
      const step = first ? first.offsetWidth + parseFloat(getComputedStyle(el).columnGap || "0") : 1;
      const i = Math.min(count - 1, Math.round(el.scrollLeft / Math.max(1, step)));
      const start = el.scrollLeft <= 2;
      const end = el.scrollLeft >= max - 2;
      setPos((p) => (p.i === i && p.start === start && p.end === end ? p : { i: end ? count - 1 : i, start, end }));
    };
    const on = () => {
      if (!raf) raf = requestAnimationFrame(update);
    };
    update();
    el.addEventListener("scroll", on, { passive: true });
    window.addEventListener("resize", on);
    return () => {
      el.removeEventListener("scroll", on);
      window.removeEventListener("resize", on);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [count]);

  const go = (dir: 1 | -1) => {
    const el = track.current;
    if (!el) return;
    const first = el.firstElementChild as HTMLElement | null;
    const step = first ? first.offsetWidth + 18 : el.clientWidth * 0.8;
    el.scrollBy({ left: dir * step, behavior: "smooth" });
  };

  // Arrastar com o mouse (toque e trackpad já rolam nativamente).
  const down = (e: React.PointerEvent) => {
    if (e.pointerType !== "mouse" || !track.current) return;
    drag.current = { x: e.clientX, left: track.current.scrollLeft, moved: false };
  };
  const move = (e: React.PointerEvent) => {
    const d = drag.current;
    const el = track.current;
    if (!d || !el) return;
    const dx = e.clientX - d.x;
    if (!d.moved && Math.abs(dx) > 5) {
      d.moved = true;
      el.setAttribute("data-dragging", "");
      el.setPointerCapture(e.pointerId);
    }
    if (d.moved) el.scrollLeft = d.left - dx;
  };
  const up = (e: React.PointerEvent) => {
    const el = track.current;
    const d = drag.current;
    drag.current = null;
    if (!el || !d) return;
    if (el.hasPointerCapture(e.pointerId)) el.releasePointerCapture(e.pointerId);
    el.removeAttribute("data-dragging");
    if (d.moved) {
      // Solta no quadro mais próximo e engole o clique que viria do arrasto.
      const first = el.firstElementChild as HTMLElement | null;
      const step = first ? first.offsetWidth + 18 : 1;
      el.scrollTo({ left: Math.round(el.scrollLeft / step) * step, behavior: "smooth" });
      const swallow = (ev: Event) => {
        ev.stopPropagation();
        ev.preventDefault();
      };
      el.addEventListener("click", swallow, { capture: true, once: true });
      setTimeout(() => el.removeEventListener("click", swallow, { capture: true }), 0);
    }
  };

  return (
    <div className="rz-car" data-reveal>
      <div
        className="rz-car-track"
        ref={track}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
      >
        {children}
      </div>
      <div className="rz-wrap rz-car-ui">
        <span className="rz-car-count" aria-live="polite">
          <b>{String(pos.i + 1).padStart(2, "0")}</b> / {String(count).padStart(2, "0")}
        </span>
        <span className="rz-car-bar" aria-hidden="true">
          <span ref={thumb} />
        </span>
        <span className="rz-car-btns">
          <button type="button" className="rz-car-btn" onClick={() => go(-1)} disabled={pos.start} aria-label="Fotos anteriores">
            <Arrow dir="left" />
          </button>
          <button type="button" className="rz-car-btn" onClick={() => go(1)} disabled={pos.end} aria-label="Próximas fotos">
            <Arrow dir="right" />
          </button>
        </span>
      </div>
    </div>
  );
}

function Arrow({ dir }: { dir: "left" | "right" }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" style={dir === "left" ? { transform: "scaleX(-1)" } : undefined}>
      <path d="M4 12h15M13 6l6 6-6 6" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Lightbox({
  fotos,
  index,
  onIndex,
  onClose,
}: {
  fotos: string[];
  index: number;
  onIndex: (i: number) => void;
  onClose: () => void;
}) {
  const fecharRef = useRef<HTMLButtonElement>(null);
  const ir = (d: number) => onIndex((index + d + fotos.length) % fotos.length);
  // Foco entra no ✕, o Tab fica preso no lightbox e volta para a miniatura ao fechar. O Esc e
  // as setas ficam no listener global abaixo (funcionam mesmo com o foco fora, após um clique).
  const { ref: trapRef, onKeyDown } = useFocusTrap<HTMLDivElement>({ initialFocus: fecharRef });

  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prevOverflow;
    };
  }, []);

  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowRight") ir(1);
      if (e.key === "ArrowLeft") ir(-1);
    };
    window.addEventListener("keydown", on);
    return () => window.removeEventListener("keydown", on);
  });

  return (
    // Clique no fundo escuro fecha (atalho de mouse; no teclado: Esc ou o ✕). Clique na foto não.
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <div
      ref={trapRef}
      className="rz-lb"
      role="dialog"
      aria-modal="true"
      aria-label="Foto ampliada"
      tabIndex={-1}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={onKeyDown}
    >
      <img key={index} src={fotos[index]} alt={`Foto ${index + 1} de ${fotos.length}`} />
      <button ref={fecharRef} type="button" className="rz-lb-x" onClick={onClose} aria-label="Fechar">
        ✕
      </button>
      {fotos.length > 1 && (
        <>
          <button type="button" className="rz-lb-nav prev" onClick={(e) => (e.stopPropagation(), ir(-1))} aria-label="Foto anterior">
            <Arrow dir="left" />
          </button>
          <button type="button" className="rz-lb-nav next" onClick={(e) => (e.stopPropagation(), ir(1))} aria-label="Próxima foto">
            <Arrow dir="right" />
          </button>
        </>
      )}
      <span className="rz-lb-count">
        {index + 1} / {fotos.length}
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------------------
   História da casa e localização.
   ------------------------------------------------------------------------- */

/** Texto da casa ao lado de uma foto do ambiente — o "sobre" deixa de ser parágrafo solto. */
export function StorySplit({ site, reverse, index }: { site: PublicSite; reverse?: boolean; index?: number }) {
  const motion = useMotion();
  const ref = useScrollVar<HTMLElement>(motion === "full", "through");
  const s = section(site, "story");
  if (!site.aboutText || !s.visible) return null;
  const fotos = photosOf(site);
  const foto = fotos[fotos.length - 1] ?? safeUrl(site.heroImageUrl);
  const [primeira, ...resto] = site.aboutText.split(/\n+/);
  return (
    <section className={`rz-section rz-story ${reverse ? "is-rev" : ""}`} id="casa" ref={ref} {...labelled(s, "casa-titulo")}>
      <div className="rz-wrap rz-story-in">
        {foto && (
          <div className="rz-story-media" data-reveal>
            <div className="rz-story-frame">
              <img src={foto} alt="" loading="lazy" />
            </div>
          </div>
        )}
        <div className="rz-story-text" data-reveal>
          <SectionHead s={{ ...s, text: "" }} id="casa-titulo" index={index} reveal={false} />
          {/* Momento da página: as palavras da frase acendem uma a uma com a rolagem
              (--sp do bloco). Sem movimento, --sp não existe e a frase nasce acesa. */}
          <p className="rz-story-quote rz-lit" aria-label={primeira}>
            {primeira.split(/\s+/).map((w, i, all) => (
              <span key={i} aria-hidden="true" style={{ ["--w" as string]: (i / Math.max(1, all.length)).toFixed(3) }}>
                {w}{" "}
              </span>
            ))}
          </p>
          {resto.map((p, i) => (
            <p key={i} className="rz-lead">
              {p}
            </p>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Endereço do Google Maps sem chave de API: a busca pelo texto do endereço, em modo embed. */
function mapEmbedUrl(addressText: string | null): string | null {
  if (!addressText) return null;
  const q = firstLines(addressText, 3).replace(/ · /g, ", ");
  return q ? `https://www.google.com/maps?q=${encodeURIComponent(q)}&output=embed` : null;
}

/** Onde estamos: o mapa de verdade, com um cartão por cima (endereço, horário e atalhos). */
export function Location({ site, index }: { site: PublicSite; index?: number }) {
  const s = section(site, "location");
  const mapa = safeUrl(site.mapsUrl);
  const insta = safeUrl(site.instagramUrl);
  const wa = bookingHref(site);
  if ((!site.addressText && !mapa && !site.openingHours) || !s.visible) return null;
  const embed = mapEmbedUrl(site.addressText);
  return (
    <section className={`rz-section rz-loc ${embed ? "has-map" : ""}`} {...labelled(s, "local")}>
      <div className="rz-wrap">
        <SectionHead s={s} id="local" index={index} />
        <div className="rz-loc-stage" data-reveal>
          {embed && (
            <div className="rz-map">
              <iframe
                src={embed}
                title={`Mapa: localização da ${site.barbershopName}`}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          )}
          <div className="rz-loc-card">
            <p className="rz-loc-name">
              <PinIcon />
              {site.barbershopName}
            </p>
            {site.addressText && (
              <div className="rz-loc-row">
                <small>Endereço</small>
                <span>{firstLines(site.addressText, 6).split(" · ").join("\n")}</span>
              </div>
            )}
            {site.openingHours && (
              <div className="rz-loc-row">
                <small>Horário</small>
                <strong>{site.openingHours}</strong>
              </div>
            )}
            <div className="rz-loc-actions">
              {mapa && (
                <a className="rz-btn rz-btn-primary" href={mapa} target="_blank" rel="noopener noreferrer">
                  Abrir no mapa
                </a>
              )}
              {wa && (
                <a className="rz-btn rz-btn-ghost" href={wa} target={site.onlineBooking ? undefined : "_blank"} rel="noopener noreferrer">
                  {site.onlineBooking ? "Agendar" : "WhatsApp"}
                </a>
              )}
              {insta && (
                <a className="rz-btn rz-btn-ghost" href={insta} target="_blank" rel="noopener noreferrer">
                  Instagram
                </a>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21Z" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="12" cy="9.5" r="2.4" fill="currentColor" />
    </svg>
  );
}

/** Tira a marcação do WhatsApp (*negrito*, _itálico_, ~riscado~): o endereço vem do texto do bot. */
export function plainText(text: string): string {
  return text.replace(/[*_~]/g, "");
}

/** Primeiras linhas do endereço, sem o emoji de pino que vem do texto do bot. */
export function firstLines(text: string, n: number): string {
  return plainText(text)
    .split(/\n+/)
    .map((l) => l.replace(/^[^\p{L}\p{N}]+/u, "").trim())
    .filter(Boolean)
    .slice(0, n)
    .join(" · ");
}
