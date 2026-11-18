// Blocos que os três templates compartilham.
//
// Serviços, equipe, planos e rodapé mostram o MESMO dado nos três — o que muda entre templates é
// a composição da página, não o conteúdo. Duplicar estes blocos faria uma correção de preço ou de
// acessibilidade precisar ser feita três vezes, e a terceira seria esquecida.
import { Link } from "react-router-dom";
import type { PublicSite } from "../api/types";

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
export const money = (v: number) => BRL.format(v);

/** Conversa no WhatsApp da barbearia, já com a mensagem de agendamento. */
export function whatsappHref(site: Pick<PublicSite, "whatsappNumber" | "barbershopName">): string | null {
  if (!site.whatsappNumber) return null;
  const digits = site.whatsappNumber.replace(/\D/g, "");
  if (!digits) return null;
  const msg = encodeURIComponent(`Olá! Quero agendar um horário na ${site.barbershopName}.`);
  return `https://wa.me/${digits}?text=${msg}`;
}

/** Caminho da página de agendamento online (rota do SPA, mesma origem). */
export const onlineBookingPath = (slug: string) => `/b/${encodeURIComponent(slug)}/agendar`;

/**
 * Para onde o "Agendar" leva — a porta de entrada do produto. Com o agendamento online ligado,
 * a página própria (o cliente vê os horários livres e marca sozinho); senão, o WhatsApp.
 */
export function bookingHref(site: PublicSite): string | null {
  if (site.onlineBooking) return onlineBookingPath(site.slug);
  return whatsappHref(site);
}

export function BookButton({ site, ghost }: { site: PublicSite; ghost?: boolean }) {
  const cls = `rz-btn ${ghost ? "rz-btn-ghost" : "rz-btn-primary"}`;
  if (site.onlineBooking) {
    // Link do router: navegação interna, sem recarregar o app nem abrir aba.
    return (
      <Link className={cls} to={onlineBookingPath(site.slug)}>
        Agendar horário
      </Link>
    );
  }
  const href = whatsappHref(site);
  // Sem WhatsApp configurado o botão não aparece: um "Agendar" que não leva a lugar nenhum é
  // pior que a ausência dele.
  if (!href) return null;
  return (
    <a className={cls} href={href} target="_blank" rel="noopener noreferrer">
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
          {/* WhatsApp continua como canal secundário mesmo com o agendamento online ligado. */}
          {whatsappHref(site) && <a href={whatsappHref(site)!} target="_blank" rel="noopener noreferrer">WhatsApp</a>}
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
  // O passo a passo descreve o WhatsApp; com agendamento online a própria página já guia.
  if (site.onlineBooking || !whatsappHref(site)) return null;
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
