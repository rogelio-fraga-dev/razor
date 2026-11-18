// Chamada final — o último convite antes do rodapé, na cor da marca.
//
// Fica separada de parts.tsx porque cada template a veste do seu jeito (faixa cheia no Clássico e
// na Vitrine, bloco editorial no Estúdio); os textos são os da seção "cta" de content.ts.
import type { PublicSite } from "../api/types";
import { BookButton } from "./parts";
import { section } from "./content";
import { Magnetic } from "./fx";

export function FinalCta({ site }: { site: PublicSite }) {
  const s = section(site, "cta");
  if (!s.visible) return null;
  return (
    <section className="rz-cta" {...(s.title ? { "aria-labelledby": "rz-cta-t" } : {})}>
      <div className="rz-cta-glow" aria-hidden="true" />
      <div className="rz-wrap rz-cta-in" data-reveal>
        {s.eyebrow && <p className="rz-cta-eyebrow">{s.eyebrow}</p>}
        {s.title && <h2 id="rz-cta-t">{s.title}</h2>}
        {s.text && <p className="rz-cta-text">{s.text}</p>}
        <div className="rz-cta-btn">
          <Magnetic strength={0.35}>
            <BookButton site={site} />
          </Magnetic>
        </div>
      </div>
    </section>
  );
}
