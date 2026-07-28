// Os três templates do site white-label.
//
// O que muda entre eles é a COMPOSIÇÃO — o que vem primeiro, quanto respiro, o peso da imagem.
// O conteúdo é o mesmo, e vem dos blocos compartilhados em parts.tsx. Por isso trocar de
// template não pede redigitar nada.
import type { PublicSite } from "../api/types";
import { BookButton, Footer, Plans, Services, Team } from "./parts";

function Logo({ site }: { site: PublicSite }) {
  if (site.logoUrl) {
    return <img src={site.logoUrl} alt={site.barbershopName} style={{ height: 40, width: "auto" }} />;
  }
  return <strong style={{ fontFamily: "var(--font-display)", fontSize: 20, letterSpacing: "-0.02em" }}>{site.barbershopName}</strong>;
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

/* ============================================================================
   CLÁSSICO — elegante e direto.
   A pergunta do visitante ("quanto custa e como marco?") é respondida acima da
   dobra: título, uma frase, botão. Sem cinema.
   ========================================================================== */
export function Classico({ site }: { site: PublicSite }) {
  return (
    <>
      <TopBar site={site} />
      <section className="rz-section" style={{ paddingBottom: 0 }}>
        <div className="rz-wrap" style={{ display: "grid", gap: 40, gridTemplateColumns: site.heroImageUrl ? "1.1fr 1fr" : "1fr", alignItems: "center" }}>
          <div>
            <h1 className="rz-h2" style={{ fontSize: "clamp(34px, 5vw, 54px)" }}>
              {site.headline || site.barbershopName}
            </h1>
            {site.subheadline && <p className="rz-lead" style={{ marginTop: 14 }}>{site.subheadline}</p>}
            <div style={{ display: "flex", gap: 12, marginTop: 28, flexWrap: "wrap" }}>
              <BookButton site={site} />
              {site.mapsUrl && (
                <a className="rz-btn rz-btn-ghost" href={site.mapsUrl} target="_blank" rel="noopener noreferrer">
                  Como chegar
                </a>
              )}
            </div>
            {site.openingHours && (
              <p style={{ marginTop: 22, fontSize: 14, color: "var(--text-muted)" }}>{site.openingHours}</p>
            )}
          </div>
          {site.heroImageUrl && (
            <img src={site.heroImageUrl} alt="" className="rz-shot" style={{ aspectRatio: "4 / 3" }} />
          )}
        </div>
      </section>

      <Services site={site} />
      <Team site={site} />
      <Plans site={site} />
      {site.aboutText && (
        <section className="rz-section rz-section-alt">
          <div className="rz-wrap">
            <h2 className="rz-h2">A casa</h2>
            <p className="rz-lead" style={{ whiteSpace: "pre-line" }}>{site.aboutText}</p>
          </div>
        </section>
      )}
      <Footer site={site} />
    </>
  );
}

/* ============================================================================
   ESTÚDIO — marca e atmosfera.
   Tipografia grande e muito respiro; a imagem é fundo, não protagonista. Feito
   para barbearia que vende experiência antes de preço — por isso o texto "sobre"
   vem ANTES da tabela de serviços.
   ========================================================================== */
export function Estudio({ site }: { site: PublicSite }) {
  return (
    <>
      <section
        style={{
          minHeight: "76vh",
          display: "grid",
          placeItems: "center",
          textAlign: "center",
          position: "relative",
          // A foto entra escurecida por baixo do texto: sem a camada, um hero claro deixaria o
          // título ilegível, e o dono não tem como saber disso ao subir a imagem.
          backgroundImage: site.heroImageUrl
            ? `linear-gradient(rgba(0,0,0,.62), rgba(0,0,0,.72)), url(${site.heroImageUrl})`
            : undefined,
          backgroundSize: "cover",
          backgroundPosition: "center",
          color: site.heroImageUrl ? "#fff" : undefined,
          padding: "80px 0",
        }}
      >
        <div className="rz-wrap">
          <div style={{ marginBottom: 26 }}>
            <Logo site={site} />
          </div>
          <h1
            style={{
              fontFamily: "var(--font-display)",
              fontSize: "clamp(40px, 8vw, 92px)",
              fontWeight: 800,
              letterSpacing: "-0.035em",
              lineHeight: 1.02,
              margin: 0,
            }}
          >
            {site.headline || site.barbershopName}
          </h1>
          {site.subheadline && (
            <p
              className="rz-lead"
              style={{ margin: "22px auto 0", color: site.heroImageUrl ? "rgba(255,255,255,.82)" : undefined }}
            >
              {site.subheadline}
            </p>
          )}
          <div style={{ marginTop: 34 }}>
            <BookButton site={site} />
          </div>
        </div>
      </section>

      {site.aboutText && (
        <section className="rz-section">
          <div className="rz-wrap" style={{ maxWidth: 760 }}>
            <p className="rz-eyebrow">Sobre</p>
            <p
              style={{
                fontFamily: "var(--font-display)",
                fontSize: "clamp(20px, 2.6vw, 27px)",
                lineHeight: 1.5,
                fontWeight: 600,
                letterSpacing: "-0.015em",
                whiteSpace: "pre-line",
                margin: 0,
              }}
            >
              {site.aboutText}
            </p>
          </div>
        </section>
      )}

      <Team site={site} />
      <Services site={site} title="A carta" />
      <Plans site={site} />
      <Footer site={site} />
    </>
  );
}

/* ============================================================================
   VITRINE — o trabalho em primeiro lugar.
   A galeria abre a página, porque para essa barbearia a prova é a foto do corte.
   Sem galeria o template cai num hero de texto em vez de mostrar molduras vazias.
   ========================================================================== */
export function Vitrine({ site }: { site: PublicSite }) {
  const shots = site.gallery.slice(0, 8);
  return (
    <>
      <TopBar site={site} />

      <section className="rz-section" style={{ paddingBottom: 0 }}>
        <div className="rz-wrap">
          <h1 className="rz-h2" style={{ fontSize: "clamp(32px, 5vw, 50px)", maxWidth: "18ch" }}>
            {site.headline || site.barbershopName}
          </h1>
          {site.subheadline && <p className="rz-lead" style={{ marginTop: 12 }}>{site.subheadline}</p>}
          <div style={{ marginTop: 24 }}>
            <BookButton site={site} />
          </div>
        </div>
      </section>

      {shots.length > 0 && (
        <section className="rz-section" aria-label="Galeria de cortes">
          <div className="rz-wrap">
            <div
              className="rz-grid"
              style={{ gridTemplateColumns: "repeat(auto-fill, minmax(210px, 1fr))" }}
            >
              {shots.map((src, i) => (
                <img key={src} src={src} alt={`Corte ${i + 1} da ${site.barbershopName}`} className="rz-shot" />
              ))}
            </div>
            {site.instagramUrl && (
              <div style={{ marginTop: 26 }}>
                <a className="rz-btn rz-btn-ghost" href={site.instagramUrl} target="_blank" rel="noopener noreferrer">
                  Ver mais no Instagram
                </a>
              </div>
            )}
          </div>
        </section>
      )}

      <Services site={site} />
      <Team site={site} />
      <Plans site={site} />
      <Footer site={site} />
    </>
  );
}

export const TEMPLATES = {
  CLASSICO: Classico,
  ESTUDIO: Estudio,
  VITRINE: Vitrine,
} as const;
