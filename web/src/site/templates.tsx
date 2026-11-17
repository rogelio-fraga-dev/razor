// Os três templates do site white-label — só a COMPOSIÇÃO de cada página.
//
// O conteúdo vem de parts.tsx (blocos idênticos nos três) e layout.tsx (capa, topo e chamada
// final, com variantes). Trocar de template muda a ordem e o peso dos blocos, nunca o dado — é o
// que sustenta a promessa de trocar de template sem redigitar nada. Nome, proposta e "ideal
// para" de cada um ficam em catalog.ts.
import type { PublicSite } from "../api/types";
import { BookButton, Footer, Gallery, HowItWorks, Plans, Services, Stats, StickyBook, Team } from "./parts";
import { About, FinalCta, Hero, TopBar } from "./layout";

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
