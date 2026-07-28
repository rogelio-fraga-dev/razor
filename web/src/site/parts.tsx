// Blocos que os três templates compartilham.
//
// Serviços, equipe, planos e rodapé mostram o MESMO dado nos três — o que muda entre templates é
// a composição da página, não o conteúdo. Duplicar estes blocos faria uma correção de preço ou de
// acessibilidade precisar ser feita três vezes, e a terceira seria esquecida.
import type { PublicSite } from "../api/types";

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
export const money = (v: number) => BRL.format(v);

/** Link de agendamento pelo WhatsApp — a porta de entrada do produto. */
export function bookingHref(site: PublicSite): string | null {
  if (!site.whatsappNumber) return null;
  const digits = site.whatsappNumber.replace(/\D/g, "");
  if (!digits) return null;
  const msg = encodeURIComponent(`Olá! Quero agendar um horário na ${site.barbershopName}.`);
  return `https://wa.me/${digits}?text=${msg}`;
}

export function BookButton({ site, ghost }: { site: PublicSite; ghost?: boolean }) {
  const href = bookingHref(site);
  // Sem WhatsApp configurado o botão não aparece: um "Agendar" que não leva a lugar nenhum é
  // pior que a ausência dele.
  if (!href) return null;
  return (
    <a className={`rz-btn ${ghost ? "rz-btn-ghost" : "rz-btn-primary"}`} href={href} target="_blank" rel="noopener noreferrer">
      Agendar pelo WhatsApp
    </a>
  );
}

export function Services({ site, title = "Serviços" }: { site: PublicSite; title?: string }) {
  if (site.services.length === 0) return null;
  return (
    <section className="rz-section" aria-labelledby="rz-servicos">
      <div className="rz-wrap">
        <p className="rz-eyebrow">O que fazemos</p>
        <h2 className="rz-h2" id="rz-servicos">{title}</h2>
        <div className="rz-card" style={{ marginTop: 26 }}>
          {site.services.map((s) => (
            <div className="rz-price-row" key={s.name}>
              <span className="rz-price-name">{s.name}</span>
              {s.durationMinutes != null && <span className="rz-price-dur">{s.durationMinutes} min</span>}
              <span className="rz-price-fill" />
              {/* Preço só existe se o dono publicou. Sem ele a linha continua útil: diz o que
                  a casa faz e quanto tempo leva. */}
              {s.price != null && <span className="rz-price-value">{money(s.price)}</span>}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export function Team({ site }: { site: PublicSite }) {
  if (site.team.length === 0) return null;
  return (
    <section className="rz-section rz-section-alt" aria-labelledby="rz-equipe">
      <div className="rz-wrap">
        <p className="rz-eyebrow">Quem corta</p>
        <h2 className="rz-h2" id="rz-equipe">Nossa equipe</h2>
        <div className="rz-grid rz-grid-4" style={{ marginTop: 30 }}>
          {site.team.map((m) => (
            <div className="rz-member" key={m.name}>
              {/* Inicial em disco de acento enquanto não há upload de foto — melhor que um
                  avatar genérico igual para todo mundo. */}
              <div className="rz-member-badge" aria-hidden="true">{m.name.charAt(0).toUpperCase()}</div>
              <div className="rz-member-name">{m.name}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export function Plans({ site }: { site: PublicSite }) {
  if (site.plans.length === 0) return null;
  return (
    <section className="rz-section" aria-labelledby="rz-planos">
      <div className="rz-wrap">
        <p className="rz-eyebrow">Clube</p>
        <h2 className="rz-h2" id="rz-planos">Planos de assinatura</h2>
        <p className="rz-lead">Corte sempre em dia, por um valor fixo no mês.</p>
        <div className="rz-grid rz-grid-3" style={{ marginTop: 30 }}>
          {site.plans.map((p, i) => (
            // O do meio é o destacado — é a convenção que o visitante já lê como "o recomendado".
            <div className={`rz-card ${i === 1 ? "rz-plan-featured" : ""}`} key={p.name}>
              <strong>{p.name}</strong>
              <div className="rz-plan-price">
                {money(p.price)} <small>/mês</small>
              </div>
              {p.description && <p className="rz-lead" style={{ fontSize: 14, marginTop: 8 }}>{p.description}</p>}
              <div style={{ marginTop: 18 }}>
                <BookButton site={site} ghost />
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export function Footer({ site }: { site: PublicSite }) {
  return (
    <footer className="rz-foot">
      <div className="rz-wrap" style={{ display: "flex", gap: 18, flexWrap: "wrap", justifyContent: "space-between" }}>
        <div style={{ maxWidth: "42ch" }}>
          <strong style={{ color: "var(--text)" }}>{site.barbershopName}</strong>
          {site.addressText && (
            // O endereço vem do bot, onde é escrito com quebras de linha e emoji.
            <p style={{ margin: "8px 0 0", whiteSpace: "pre-line" }}>{site.addressText}</p>
          )}
          {site.openingHours && <p style={{ margin: "8px 0 0" }}>{site.openingHours}</p>}
        </div>
        <div style={{ display: "flex", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
          {site.mapsUrl && <a href={site.mapsUrl} target="_blank" rel="noopener noreferrer">Como chegar</a>}
          {site.instagramUrl && <a href={site.instagramUrl} target="_blank" rel="noopener noreferrer">Instagram</a>}
          {bookingHref(site) && <a href={bookingHref(site)!} target="_blank" rel="noopener noreferrer">WhatsApp</a>}
        </div>
      </div>
    </footer>
  );
}
