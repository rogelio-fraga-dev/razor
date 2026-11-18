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

/**
 * Cada template mostra a equipe do seu jeito (CSS `.rz-team-*` no fim de site.css): o Clássico
 * em cartões compactos, o Estúdio em retrato grande (a equipe é o destaque dele) e a Vitrine com a
 * foto de ponta a ponta (lá a imagem é o argumento). O dado é o mesmo nos três.
 */
const TEAM_VARIANT: Record<PublicSite["template"], "cards" | "portrait" | "tiles"> = {
  CLASSICO: "cards",
  ESTUDIO: "portrait",
  VITRINE: "tiles",
};

/** Defesa extra além do backend: só data URI de imagem vira <img>, só http(s) vira link. */
const isPhoto = (u: string | null) => !!u && /^data:image\/(jpeg|png|webp);base64,/.test(u);
const isHttp = (u: string | null) => !!u && /^https?:\/\//i.test(u);

function igHandle(url: string): string {
  const m = url.match(/instagram\.com\/([A-Za-z0-9._]+)/i);
  return m ? `@${m[1]}` : "Instagram";
}

export function Team({ site }: { site: PublicSite }) {
  if (site.team.length === 0) return null;
  const variant = TEAM_VARIANT[site.template] ?? "cards";
  return (
    <section className="rz-section rz-section-alt" aria-labelledby="rz-equipe">
      <div className="rz-wrap">
        <p className="rz-eyebrow">Quem corta</p>
        <h2 className="rz-h2" id="rz-equipe">Nossa equipe</h2>
        <div className={`rz-team rz-team-${variant}`}>
          {site.team.map((m, i) => {
            // O apelido é como a casa chama o barbeiro — é o nome que o cliente vai pedir.
            const shown = m.nickname || m.name;
            return (
              <article className="rz-team-member" key={`${m.name}-${i}`}>
                {isPhoto(m.photoUrl) ? (
                  <img className="rz-team-photo" src={m.photoUrl!} alt={`Foto de ${shown}`} loading="lazy" />
                ) : (
                  // Sem foto: inicial em disco de acento — melhor que um avatar genérico igual
                  // para todo mundo.
                  <div className="rz-team-initial" aria-hidden="true">{shown.charAt(0).toUpperCase()}</div>
                )}
                <h3 className="rz-team-name">{shown}</h3>
                {m.nickname && m.nickname !== m.name && <div className="rz-team-fullname">{m.name}</div>}
                {m.bio && <p className="rz-team-bio">{m.bio}</p>}
                {m.specialties.length > 0 && (
                  <ul className="rz-team-tags" aria-label={`Especialidades de ${shown}`}>
                    {m.specialties.map((t) => (
                      <li key={t}>{t}</li>
                    ))}
                  </ul>
                )}
                {isHttp(m.instagramUrl) && (
                  <a className="rz-team-ig" href={m.instagramUrl!} target="_blank" rel="noopener noreferrer">
                    {igHandle(m.instagramUrl!)}
                  </a>
                )}
              </article>
            );
          })}
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

/**
 * Faixa de números logo abaixo do hero.
 *
 * Tudo aqui é DERIVADO do que a barbearia realmente cadastrou — nada de "mais de 1000 clientes
 * satisfeitos". Número inventado num site é o mesmo problema que o painel já resolveu ao remover
 * os dados fabricados: some a confiança quando alguém confere.
 */
export function Stats({ site }: { site: PublicSite }) {
  const itens: { v: string; l: string }[] = [];
  if (site.team.length > 0) {
    itens.push({ v: String(site.team.length), l: site.team.length === 1 ? "barbeiro" : "barbeiros na equipe" });
  }
  if (site.services.length > 0) {
    itens.push({ v: String(site.services.length), l: "serviços no cardápio" });
  }
  const comPreco = site.services.filter((s) => s.price != null);
  if (comPreco.length > 0) {
    itens.push({ v: money(Math.min(...comPreco.map((s) => s.price!))), l: "a partir de" });
  }
  if (site.plans.length > 0) {
    itens.push({ v: money(Math.min(...site.plans.map((p) => p.price))), l: "clube por mês" });
  }
  if (itens.length < 2) return null;

  return (
    <div className="rz-stats">
      {itens.map((i) => (
        <div className="rz-stat" key={i.l}>
          <div className="rz-stat-v">{i.v}</div>
          <div className="rz-stat-l">{i.l}</div>
        </div>
      ))}
    </div>
  );
}

/** Galeria do trabalho. Em mosaico a primeira foto vira capa; sem fotos, a seção não existe. */
export function Gallery({ site, title = "Nosso trabalho", mosaic }: { site: PublicSite; title?: string; mosaic?: boolean }) {
  const fotos = site.gallery.slice(0, mosaic ? 9 : 8);
  if (fotos.length === 0) return null;
  return (
    <section className={`rz-section ${mosaic ? "" : "rz-section-alt"}`} aria-label={title || "Galeria"}>
      <div className="rz-wrap">
        {title && (
          <>
            <p className="rz-eyebrow">Portfólio</p>
            <h2 className="rz-h2">{title}</h2>
          </>
        )}
        <div
          className={mosaic ? "rz-mosaic" : "rz-grid"}
          style={mosaic ? { marginTop: title ? 26 : 0 } : { gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", marginTop: 26 }}
        >
          {fotos.map((src, i) => (
            <img
              key={src}
              src={src}
              alt={`Corte ${i + 1} da ${site.barbershopName}`}
              className={mosaic ? undefined : "rz-shot"}
              loading={i < 2 ? "eager" : "lazy"}
            />
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
  );
}

/**
 * "Como funciona" em três passos.
 *
 * Existe porque agendar por WhatsApp com um robô é novo para muita gente: sem explicar, o
 * visitante hesita justamente no clique que o site inteiro persegue.
 */
export function HowItWorks({ site }: { site: PublicSite }) {
  if (!bookingHref(site)) return null;
  return (
    <section className="rz-section rz-section-alt">
      <div className="rz-wrap">
        <p className="rz-eyebrow">Simples assim</p>
        <h2 className="rz-h2">Como agendar</h2>
        <div className="rz-steps" style={{ marginTop: 34 }}>
          <div className="rz-step">
            <h3>Chama no WhatsApp</h3>
            <p>Sem baixar app e sem criar conta. É a mesma conversa que você já usa todo dia.</p>
          </div>
          <div className="rz-step">
            <h3>Escolhe o horário</h3>
            <p>Respondemos na hora com os horários livres — pode mandar áudio se preferir.</p>
          </div>
          <div className="rz-step">
            <h3>Pronto</h3>
            <p>Você recebe a confirmação e um lembrete uma hora antes. Só chegar.</p>
          </div>
        </div>
      </div>
    </section>
  );
}

/** Barra fixa de agendamento no celular — o botão do topo some ao rolar. */
export function StickyBook({ site }: { site: PublicSite }) {
  if (!bookingHref(site)) return null;
  return (
    <div className="rz-sticky">
      <BookButton site={site} />
    </div>
  );
}
