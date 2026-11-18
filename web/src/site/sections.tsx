// Seções com VARIANTE por template — o que dá a cada modelo uma identidade própria.
//
// parts.tsx guarda os blocos que são iguais nos três (equipe, planos, rodapé). Aqui ficam os que
// mudam de forma: o menu de serviços vira quadro de barbearia no Clássico, lista editorial no
// Estúdio e cartões com foto na Vitrine. O DADO continua o mesmo — trocar de template nunca pede
// para redigitar nada.
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import type { PublicSite } from "../api/types";
import { BookButton, bookingHref, money } from "./parts";
import { useFocusTrap } from "../lib/useFocusTrap";

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

/* ---------------------------------------------------------------------------
   Casca do template: fontes próprias e animação de entrada das seções.
   ------------------------------------------------------------------------- */

const FONTS: Record<TemplateKey, string> = {
  classico: "family=Playfair+Display:ital,wght@0,600;0,800;1,600&family=Inter:wght@400;500;600;700",
  estudio: "family=Fraunces:opsz,wght@9..144,300;9..144,500;9..144,700&family=Inter:wght@300;400;500;600",
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

/**
 * Revela cada `[data-reveal]` quando entra na tela. Sem IntersectionObserver (ou com
 * movimento reduzido) tudo já nasce visível — animação nunca pode esconder conteúdo.
 */
function useReveal(root: React.RefObject<HTMLElement>) {
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const items = Array.from(el.querySelectorAll<HTMLElement>("[data-reveal]"));
    const reduz = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (!("IntersectionObserver" in window) || reduz) {
      items.forEach((i) => i.classList.add("is-in"));
      return;
    }
    const io = new IntersectionObserver(
      (entries) =>
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("is-in");
            io.unobserve(e.target);
          }
        }),
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 },
    );
    items.forEach((i) => io.observe(i));
    return () => io.disconnect();
  });
}

export function TemplateShell({ tpl, children }: { tpl: TemplateKey; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useTemplateFonts(tpl);
  useReveal(ref);
  return (
    <div ref={ref} className={`tpl tpl-${tpl}`}>
      {children}
    </div>
  );
}

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

  // Só aparecem âncoras para seções que existem — link para bloco vazio é link quebrado.
  const links: { href: string; label: string }[] = [];
  if (site.services.length) links.push({ href: "#servicos", label: "Serviços" });
  if (site.gallery.length) links.push({ href: "#trabalho", label: "Trabalhos" });
  if (site.team.length) links.push({ href: "#equipe", label: "Equipe" });
  if (site.plans.length) links.push({ href: "#planos", label: "Clube" });
  if (site.addressText || site.mapsUrl) links.push({ href: "#local", label: "Onde estamos" });

  const logo = safeUrl(site.logoUrl);
  return (
    <header className={`rz-nav ${scrolled || solid ? "is-solid" : ""}`}>
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

/** Clássico: texto à esquerda, foto em moldura de arco à direita e cartão com horário. */
export function HeroSplit({ site }: { site: PublicSite }) {
  const foto = safeUrl(site.heroImageUrl) ?? safeUrl(site.gallery[0]);
  const mapa = safeUrl(site.mapsUrl);
  return (
    <section className="rz-hsplit" id="topo">
      <div className="rz-wrap rz-hsplit-in">
        <div className="rz-hsplit-text" data-reveal>
          <p className="rz-eyebrow">Barbearia · desde a primeira navalha</p>
          <h1>{site.headline || site.barbershopName}</h1>
          {site.subheadline && <p className="rz-lead">{site.subheadline}</p>}
          <div className="rz-hero-cta">
            <BookButton site={site} />
            {mapa && (
              <a className="rz-btn rz-btn-ghost" href={mapa} target="_blank" rel="noopener noreferrer">
                Como chegar
              </a>
            )}
          </div>
        </div>
        <div className="rz-hsplit-media" data-reveal>
          {foto ? <img src={foto} alt="" /> : <div className="rz-hsplit-ph" aria-hidden="true" />}
          {(site.openingHours || site.addressText) && (
            <div className="rz-hsplit-card">
              {site.openingHours && (
                <>
                  <small>Horário</small>
                  <strong>{site.openingHours}</strong>
                </>
              )}
              {site.addressText && (
                <>
                  <small>Endereço</small>
                  <span>{firstLines(site.addressText, 2)}</span>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

/** Estúdio: foto em tela cheia, nome gigante centralizado, linhas finas. */
export function HeroEditorial({ site }: { site: PublicSite }) {
  const foto = safeUrl(site.heroImageUrl) ?? safeUrl(site.gallery[0]);
  return (
    <section className="rz-hed" id="topo">
      {foto && <div className="rz-hed-bg" style={{ backgroundImage: `url("${foto}")` }} aria-hidden="true" />}
      <div className="rz-hed-veil" aria-hidden="true" />
      <div className="rz-wrap rz-hed-in">
        <p className="rz-hed-kicker" data-reveal>
          {site.barbershopName}
        </p>
        <h1 data-reveal>{site.headline || site.barbershopName}</h1>
        {site.subheadline && (
          <p className="rz-hed-sub" data-reveal>
            {site.subheadline}
          </p>
        )}
        <div className="rz-hero-cta" data-reveal>
          <BookButton site={site} />
        </div>
        {site.openingHours && <p className="rz-hed-hours">{site.openingHours}</p>}
      </div>
      <a href="#casa" className="rz-hed-scroll" aria-label="Rolar para o conteúdo">
        <span />
      </a>
    </section>
  );
}

/** Vitrine: título condensado enorme e a faixa de serviços rolando logo abaixo. */
export function HeroPoster({ site }: { site: PublicSite }) {
  return (
    <section className="rz-poster" id="topo">
      <div className="rz-wrap">
        <p className="rz-eyebrow" data-reveal>
          {site.barbershopName}
        </p>
        <h1 data-reveal>{site.headline || site.barbershopName}</h1>
        <div className="rz-poster-row" data-reveal>
          {site.subheadline && <p className="rz-lead">{site.subheadline}</p>}
          <BookButton site={site} />
        </div>
      </div>
    </section>
  );
}

/* ---------------------------------------------------------------------------
   Faixas decorativas.
   ------------------------------------------------------------------------- */

/** Faixa rolando com os serviços — movimento que vende "aqui tem de tudo" sem texto. */
export function Marquee({ site }: { site: PublicSite }) {
  const itens = site.services.map((s) => s.name);
  if (itens.length === 0) return null;
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

function ServicesHead({ eyebrow, title, center }: { eyebrow: string; title: string; center?: boolean }) {
  return (
    <div className={center ? "rz-head-center" : undefined} data-reveal>
      <p className="rz-eyebrow">{eyebrow}</p>
      <h2 className="rz-h2" id="servicos">
        {title}
      </h2>
    </div>
  );
}

/** Clássico: quadro de preços em duas colunas, com pontilhado ligando nome e valor. */
export function ServicesBoard({ site }: { site: PublicSite }) {
  if (site.services.length === 0) return null;
  return (
    <section className="rz-section" aria-labelledby="servicos">
      <div className="rz-wrap">
        <ServicesHead eyebrow="Tabela da casa" title="Serviços & valores" center />
        <div className="rz-board" data-reveal>
          {site.services.map((s) => (
            <div className="rz-board-row" key={s.name}>
              <div className="rz-board-line">
                <span className="rz-board-name">{s.name}</span>
                <span className="rz-board-dots" />
                {s.price != null && <span className="rz-board-price">{money(s.price)}</span>}
              </div>
              {s.durationMinutes != null && <span className="rz-board-dur">{s.durationMinutes} minutos</span>}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/** Estúdio: lista numerada, tipografia grande e linhas finas — cardápio de restaurante caro. */
export function ServicesEditorial({ site }: { site: PublicSite }) {
  if (site.services.length === 0) return null;
  return (
    <section className="rz-section" aria-labelledby="servicos">
      <div className="rz-wrap">
        <ServicesHead eyebrow="A carta" title="Serviços" />
        <ol className="rz-ed-list">
          {site.services.map((s, i) => (
            <li key={s.name} data-reveal>
              <span className="rz-ed-n">{String(i + 1).padStart(2, "0")}</span>
              <span className="rz-ed-name">{s.name}</span>
              {s.durationMinutes != null && <span className="rz-ed-dur">{s.durationMinutes} min</span>}
              {s.price != null && <span className="rz-ed-price">{money(s.price)}</span>}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

/** Vitrine: cartões com foto do trabalho ao fundo. As fotos vêm da galeria, em rodízio. */
export function ServicesCards({ site }: { site: PublicSite }) {
  if (site.services.length === 0) return null;
  const fotos = site.gallery.map((g) => safeUrl(g)).filter(Boolean) as string[];
  return (
    <section className="rz-section" aria-labelledby="servicos">
      <div className="rz-wrap">
        <ServicesHead eyebrow="Cardápio" title="O que a gente faz" />
        <div className="rz-scards">
          {site.services.map((s, i) => {
            const f = fotos.length ? fotos[(i + 1) % fotos.length] : null;
            return (
              <article className="rz-scard" key={s.name} data-reveal>
                {f && <div className="rz-scard-bg" style={{ backgroundImage: `url("${f}")` }} aria-hidden="true" />}
                <div className="rz-scard-body">
                  <h3>{s.name}</h3>
                  <div className="rz-scard-meta">
                    {s.durationMinutes != null && <span>{s.durationMinutes} min</span>}
                    {s.price != null && <strong>{money(s.price)}</strong>}
                  </div>
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

type GalleryVariant = "frames" | "strip" | "masonry";

export function GalleryPro({
  site,
  variant,
  title,
  eyebrow = "Portfólio",
}: {
  site: PublicSite;
  variant: GalleryVariant;
  title: string;
  eyebrow?: string;
}) {
  const todas = (site.gallery.map((g) => safeUrl(g)).filter(Boolean) as string[]).slice(0, 12);
  // Molduras são grade de 4: só linhas completas (a última com 2 fotos parece site inacabado).
  const fotos = variant === "frames" && todas.length > 4 ? todas.slice(0, todas.length - (todas.length % 4)) : todas;
  const [aberta, setAberta] = useState<number | null>(null);
  const fechar = useCallback(() => setAberta(null), []);
  if (fotos.length === 0) return null;
  const insta = safeUrl(site.instagramUrl);

  return (
    <section className={`rz-section rz-gal rz-gal-${variant}`} aria-labelledby="trabalho">
      <div className="rz-wrap">
        <div data-reveal>
          <p className="rz-eyebrow">{eyebrow}</p>
          <h2 className="rz-h2" id="trabalho">
            {title}
          </h2>
        </div>
        <div className="rz-gal-grid">
          {fotos.map((src, i) => (
            <button
              type="button"
              key={src}
              className="rz-gal-item"
              onClick={() => setAberta(i)}
              aria-label={`Ampliar foto ${i + 1} de ${fotos.length}`}
              data-reveal
            >
              <img src={src} alt={`Trabalho ${i + 1} da ${site.barbershopName}`} loading={i < 3 ? "eager" : "lazy"} />
            </button>
          ))}
        </div>
        {insta && (
          <div className="rz-gal-more" data-reveal>
            <a className="rz-btn rz-btn-ghost" href={insta} target="_blank" rel="noopener noreferrer">
              Ver mais no Instagram
            </a>
          </div>
        )}
      </div>
      {aberta != null && <Lightbox fotos={fotos} index={aberta} onIndex={setAberta} onClose={fechar} />}
    </section>
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
      <img src={fotos[index]} alt={`Foto ${index + 1} de ${fotos.length}`} />
      <button ref={fecharRef} type="button" className="rz-lb-x" onClick={onClose} aria-label="Fechar">
        ✕
      </button>
      {fotos.length > 1 && (
        <>
          <button type="button" className="rz-lb-nav prev" onClick={(e) => (e.stopPropagation(), ir(-1))} aria-label="Foto anterior">
            ‹
          </button>
          <button type="button" className="rz-lb-nav next" onClick={(e) => (e.stopPropagation(), ir(1))} aria-label="Próxima foto">
            ›
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
export function StorySplit({ site, reverse, eyebrow = "A casa" }: { site: PublicSite; reverse?: boolean; eyebrow?: string }) {
  if (!site.aboutText) return null;
  const fotos = site.gallery.map((g) => safeUrl(g)).filter(Boolean) as string[];
  const foto = fotos[fotos.length - 1] ?? safeUrl(site.heroImageUrl);
  const [primeira, ...resto] = site.aboutText.split(/\n+/);
  return (
    <section className={`rz-section rz-story ${reverse ? "is-rev" : ""}`} id="casa">
      <div className="rz-wrap rz-story-in">
        {foto && (
          <div className="rz-story-media" data-reveal>
            <img src={foto} alt="" loading="lazy" />
          </div>
        )}
        <div className="rz-story-text" data-reveal>
          <p className="rz-eyebrow">{eyebrow}</p>
          <p className="rz-story-quote">{primeira}</p>
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

/** Onde estamos: endereço, horário e os três atalhos (rota, WhatsApp, Instagram). */
export function Location({ site }: { site: PublicSite }) {
  const mapa = safeUrl(site.mapsUrl);
  const insta = safeUrl(site.instagramUrl);
  const wa = bookingHref(site);
  if (!site.addressText && !mapa && !site.openingHours) return null;
  return (
    <section className="rz-section rz-section-alt" aria-labelledby="local">
      <div className="rz-wrap rz-loc">
        <div data-reveal>
          <p className="rz-eyebrow">Visite</p>
          <h2 className="rz-h2" id="local">
            Onde estamos
          </h2>
          {site.addressText && <p className="rz-loc-addr">{plainText(site.addressText)}</p>}
        </div>
        <div className="rz-loc-card" data-reveal>
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
              <a className="rz-btn rz-btn-ghost" href={wa} target="_blank" rel="noopener noreferrer">
                WhatsApp
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
    </section>
  );
}

/** Tira a marcação do WhatsApp (*negrito*, _itálico_, ~riscado~): o endereço vem do texto do bot. */
export function plainText(text: string): string {
  return text.replace(/[*_~]/g, "");
}

/** Primeiras linhas do endereço, sem o emoji de pino que vem do texto do bot. */
function firstLines(text: string, n: number): string {
  return plainText(text)
    .split(/\n+/)
    .map((l) => l.replace(/^[^\p{L}\p{N}]+/u, "").trim())
    .filter(Boolean)
    .slice(0, n)
    .join(" · ");
}
