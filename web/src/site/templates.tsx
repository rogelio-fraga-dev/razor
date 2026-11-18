// Os três templates do site white-label — só a COMPOSIÇÃO de cada página.
//
// O conteúdo vem de parts.tsx (blocos idênticos nos três), sections.tsx (blocos com variante
// por template) e layout.tsx (chamada final). Cada template tem identidade própria — fonte,
// capa, peça-assinatura em 3D, forma do menu de serviços e da galeria —, mas o DADO é sempre o
// mesmo: trocar de template nunca pede para redigitar nada. Os textos de cada seção vêm de
// content.ts; nome, proposta e "ideal para" ficam em catalog.ts; o visual, nos blocos `.tpl-*`
// de site.css.
import type { PublicSite } from "../api/types";
import { Footer, HowItWorks, Plans, Stats, StickyBook, Team } from "./parts";
import { FinalCta } from "./layout";
import { bookLabel } from "./content";
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

/* ============================================================================
   CLÁSSICO — barbearia tradicional.
   Serifa, poste de barbeiro girando em 3D, quadro de preços em moldura dupla.
   Responde primeiro "quanto custa e quando abre" — quem compete por conveniência.
   ========================================================================== */
export function Classico({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="classico">
      <NavBar site={site} />
      <HeroSplit site={site} />
      <BarberStripe />
      <Stats site={site} />
      <ServicesBoard site={site} />
      <GalleryPro site={site} variant="frames" />
      <StorySplit site={site} />
      <Team site={site} />
      <Plans site={site} />
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
   Capa em tela cheia com a navalha 3D, tipografia grande, fotos em preto e
   branco e seções numeradas. A marca aparece antes do preço.
   ========================================================================== */
export function Estudio({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="estudio">
      <NavBar site={site} />
      <HeroEditorial site={site} />
      <StorySplit site={site} index={1} />
      <GalleryPro site={site} variant="carousel" index={2} />
      <Team site={site} />
      <ServicesEditorial site={site} index={3} />
      <Plans site={site} />
      <Location site={site} index={4} />
      <FinalCta site={site} />
      <Footer site={site} />
      <StickyBook site={site} />
    </TemplateShell>
  );
}

/* ============================================================================
   VITRINE — o trabalho é o argumento.
   Título em cartaz, anel 3D de fotos, faixa de serviços rolando, mosaico e
   cartões que inclinam com o ponteiro. Para quem já vende pelo Instagram.
   ========================================================================== */
export function Vitrine({ site }: { site: PublicSite }) {
  return (
    <TemplateShell tpl="vitrine">
      <NavBar site={site} solid />
      <HeroPoster site={site} bookLabel={bookLabel(site)} />
      <Marquee site={site} />
      <GalleryPro site={site} variant="masonry" />
      <ServicesCards site={site} />
      <Stats site={site} />
      <Team site={site} />
      <Plans site={site} />
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
