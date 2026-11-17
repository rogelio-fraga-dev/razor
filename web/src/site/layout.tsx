// Blocos de abertura e fechamento dos templates: logo, barra de topo, capa (hero), texto da casa
// e a chamada final. Ficam separados de parts.tsx porque cada template os usa com uma VARIANTE
// diferente (capa centralizada no Estúdio, barra enxuta na Vitrine), enquanto os blocos de
// conteúdo de parts.tsx são idênticos nos três.
import type { PublicSite } from "../api/types";
import { BookButton } from "./parts";

export function Logo({ site, light }: { site: PublicSite; light?: boolean }) {
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

export function TopBar({ site }: { site: PublicSite }) {
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
export function Hero({ site, align = "left" }: { site: PublicSite; align?: "left" | "center" }) {
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

export function About({ site, big }: { site: PublicSite; big?: boolean }) {
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
export function FinalCta({ site }: { site: PublicSite }) {
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
