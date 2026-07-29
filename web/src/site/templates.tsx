// Os três templates do site white-label.
//
// Cada um é uma mini landing page completa: hero com imagem, prova em números, o que fazemos,
// galeria do trabalho, equipe, planos, como funciona e uma chamada final. O que muda entre eles
// é a ORDEM e o peso de cada bloco — o conteúdo é o mesmo e vem de parts.tsx, que é o que
// sustenta a promessa de trocar de template sem redigitar nada.
import type { PublicSite } from "../api/types";
import { BookButton, Footer, Gallery, HowItWorks, Plans, Services, Stats, StickyBook, Team } from "./parts";

function Logo({ site, light }: { site: PublicSite; light?: boolean }) {
  if (site.logoUrl) {
    return <img src={site.logoUrl} alt={site.barbershopName} style={{ height: 40, width: "auto" }} />;
  }
  return (
    <strong
      style={{
        fontFamily: "var(--font-display)",
        fontSize: 20,
        letterSpacing: "-0.02em",
        color: light ? "#fff" : undefined,
      }}
    >
      {site.barbershopName}
    </strong>
  );
}

function TopBar({ site }: { site: PublicSite }) {
  return (
    <header style={{ borderBottom: "1px solid var(--border)" }}>
      <div
        className="rz-wrap"
        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, padding: "18px 24px" }}
      >
        <Logo site={site} />
        <BookButton site={site} />
      </div>
    </header>
  );
}

/**
 * Hero de tela cheia com a foto do dono ao fundo.
 *
 * Sem foto, cai num bloco sólido com a cor da marca — o que evita o hero mais comum de site
 * ruim: um retângulo cinza esperando imagem.
 */
function Hero({ site, align = "left" }: { site: PublicSite; align?: "left" | "center" }) {
  const semFoto = !site.heroImageUrl;
  return (
    <section className="rz-hero" style={semFoto ? { background: "linear-gradient(140deg, var(--accent), var(--accent-hover))" } : undefined}>
      {site.heroImageUrl && (
        <>
          <div className="rz-hero-bg" style={{ backgroundImage: `url(${site.heroImageUrl})` }} aria-hidden="true" />
          <div className="rz-hero-veil" aria-hidden="true" />
        </>
      )}
      <div className="rz-hero-inner">
        <div className="rz-wrap" style={align === "center" ? { textAlign: "center" } : undefined}>
          <div style={{ marginBottom: 22 }}>
            <Logo site={site} light />
          </div>
          <h1 style={align === "center" ? { marginInline: "auto" } : undefined}>
            {site.headline || site.barbershopName}
          </h1>
          {site.subheadline && (
            <p className="rz-lead" style={align === "center" ? { marginInline: "auto" } : undefined}>
              {site.subheadline}
            </p>
          )}
          <div className="rz-hero-cta" style={align === "center" ? { justifyContent: "center" } : undefined}>
            <BookButton site={site} />
            {site.mapsUrl && (
              <a className="rz-btn rz-btn-ghost" href={site.mapsUrl} target="_blank" rel="noopener noreferrer">
                Como chegar
              </a>
            )}
          </div>
          {site.openingHours && (
            <p style={{ marginTop: 22, fontSize: 14, color: "rgba(255,255,255,.7)" }}>{site.openingHours}</p>
          )}
        </div>
      </div>
    </section>
  );
}

function About({ site, big }: { site: PublicSite; big?: boolean }) {
  if (!site.aboutText) return null;
  return (
    <section className="rz-section">
      <div className="rz-wrap" style={{ maxWidth: big ? 800 : undefined }}>
        <p className="rz-eyebrow">A casa</p>
        <p
          className={big ? undefined : "rz-lead"}
          style={
            big
              ? {
                  fontFamily: "var(--font-display)",
                  fontSize: "clamp(20px, 2.6vw, 28px)",
                  lineHeight: 1.5,
                  fontWeight: 600,
                  letterSpacing: "-0.015em",
                  whiteSpace: "pre-line",
                  margin: 0,
                }
              : { whiteSpace: "pre-line" }
          }
        >
          {site.aboutText}
        </p>
      </div>
    </section>
  );
}

/** Chamada final antes do rodapé — a última oportunidade de converter a visita. */
function FinalCta({ site }: { site: PublicSite }) {
  return (
    <section className="rz-cta">
      <div className="rz-wrap">
        <h2>Bora marcar seu horário?</h2>
        <p>Chame no WhatsApp e a gente resolve em um minuto — sem app, sem cadastro.</p>
        <div>
          <BookButton site={site} />
        </div>
      </div>
    </section>
  );
}

/* ============================================================================
   CLÁSSICO — a pergunta do visitante respondida primeiro.
   Hero, números, serviços e preço logo em seguida. Para a barbearia de bairro
   que compete por conveniência e preço claro.
   ========================================================================== */
export function Classico({ site }: { site: PublicSite }) {
  return (
    <>
      <Hero site={site} />
      <Stats site={site} />
      <Services site={site} />
      <Gallery site={site} title="Nosso trabalho" />
      <Team site={site} />
      <Plans site={site} />
      <HowItWorks site={site} />
      <About site={site} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </>
  );
}

/* ============================================================================
   ESTÚDIO — marca antes de preço.
   Hero centralizado, texto grande, equipe em destaque; a tabela de valores só
   aparece depois que a barbearia se apresentou.
   ========================================================================== */
export function Estudio({ site }: { site: PublicSite }) {
  return (
    <>
      <Hero site={site} align="center" />
      <About site={site} big />
      <Gallery site={site} title="Atmosfera" />
      <Team site={site} />
      <Stats site={site} />
      <Services site={site} title="A carta" />
      <Plans site={site} />
      <HowItWorks site={site} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </>
  );
}

/* ============================================================================
   VITRINE — o trabalho é o argumento.
   Barra de topo enxuta e a galeria logo abaixo do título: para essa barbearia a
   prova é a foto do corte, não o texto.
   ========================================================================== */
export function Vitrine({ site }: { site: PublicSite }) {
  return (
    <>
      <TopBar site={site} />
      <section className="rz-section" style={{ paddingBottom: 0 }}>
        <div className="rz-wrap">
          <p className="rz-eyebrow">Portfólio</p>
          <h1 className="rz-h2" style={{ fontSize: "clamp(32px, 5.4vw, 56px)", maxWidth: "17ch" }}>
            {site.headline || site.barbershopName}
          </h1>
          {site.subheadline && <p className="rz-lead" style={{ marginTop: 12 }}>{site.subheadline}</p>}
          <div style={{ marginTop: 26 }}>
            <BookButton site={site} />
          </div>
        </div>
      </section>
      <Gallery site={site} title="" mosaic />
      <Stats site={site} />
      <Services site={site} />
      <Team site={site} />
      <Plans site={site} />
      <HowItWorks site={site} />
      <About site={site} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </>
  );
}

export const TEMPLATES = {
  CLASSICO: Classico,
  ESTUDIO: Estudio,
  VITRINE: Vitrine,
} as const;
