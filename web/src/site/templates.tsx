// Os três templates do site white-label — só a COMPOSIÇÃO de cada página.
//
// O conteúdo vem de parts.tsx (blocos idênticos nos três), sections.tsx (blocos com variante
// por template) e layout.tsx (chamada final). Cada template tem identidade própria — fonte,
// capa, forma do menu de serviços e da galeria —, mas o DADO é sempre o mesmo: trocar de
// template nunca pede para redigitar nada. Nome, proposta e "ideal para" ficam em catalog.ts;
// o visual de cada um, nos blocos `.tpl-*` de site.css.
import type { ReactNode } from "react";
import type { PublicSite } from "../api/types";
import { Footer, HowItWorks, Plans, Stats, StickyBook, Team } from "./parts";
import { FinalCta } from "./layout";
import {
  BarberStripe,
  GalleryPro,
  HeroEditorial,
  HeroPoster,
  HeroSplit,
  Location,
  Marquee,
  NavBar,
  ServicesBoard,
  ServicesCards,
  ServicesEditorial,
  StorySplit,
  TemplateShell,
} from "./sections";

/** Âncora da navegação para blocos que vêm de parts.tsx. */
const Anchor = ({ id, children }: { id: string; children: ReactNode }) => (
  <div id={id} className="rz-anchor" data-reveal>
    {children}
  </div>
);

/* ============================================================================
   CLÁSSICO — barbearia tradicional.
   Serifa, listras de poste de barbeiro, quadro de preços. Responde primeiro
   "quanto custa e quando abre" — quem compete por conveniência.
   ========================================================================== */
export function Classico({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="classico">
      <NavBar site={site} />
      <HeroSplit site={site} />
      <BarberStripe />
      <Stats site={site} />
      <ServicesBoard site={site} />
      <GalleryPro site={site} variant="frames" title="Nosso trabalho" />
      <StorySplit site={site} />
      <Anchor id="equipe"><Team site={site} /></Anchor>
      <Anchor id="planos"><Plans site={site} /></Anchor>
      <HowItWorks site={site} />
      <Location site={site} />
      <BarberStripe />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </TemplateShell>
  );
}

/* ============================================================================
   ESTÚDIO — editorial premium.
   Capa em tela cheia, tipografia grande, fotos em preto e branco que ganham
   cor ao passar o mouse. A marca aparece antes do preço.
   ========================================================================== */
export function Estudio({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="estudio">
      <NavBar site={site} />
      <HeroEditorial site={site} />
      <StorySplit site={site} eyebrow="Manifesto" />
      <GalleryPro site={site} variant="strip" title="Atmosfera" eyebrow="Estúdio" />
      <Anchor id="equipe"><Team site={site} /></Anchor>
      <ServicesEditorial site={site} />
      <Anchor id="planos"><Plans site={site} /></Anchor>
      <Location site={site} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </TemplateShell>
  );
}

/* ============================================================================
   VITRINE — o trabalho é o argumento.
   Título em cartaz, faixa de serviços rolando e mosaico de fotos com zoom.
   Para quem já vende pelo Instagram.
   ========================================================================== */
export function Vitrine({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="vitrine">
      <NavBar site={site} solid />
      <HeroPoster site={site} />
      <Marquee site={site} />
      <GalleryPro site={site} variant="masonry" title="Feito aqui" eyebrow="Portfólio" />
      <ServicesCards site={site} />
      <Stats site={site} />
      <Anchor id="equipe"><Team site={site} /></Anchor>
      <Anchor id="planos"><Plans site={site} /></Anchor>
      <StorySplit site={site} reverse />
      <HowItWorks site={site} />
      <Location site={site} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </TemplateShell>
  );
}

export const TEMPLATES = {
  CLASSICO: Classico,
  ESTUDIO: Estudio,
  VITRINE: Vitrine,
} as const;
