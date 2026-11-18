// Página pública do site white-label: /b/:slug
//
// Fica FORA do bloco autenticado do App — é um site, e site não pede login. O tenant vem do slug
// da URL e o backend recusa slug inexistente ou site não publicado com o mesmo 404.
import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import type { PublicSite, SiteTemplate, SiteTheme } from "../api/types";
import { TEMPLATES } from "./templates";
import { bookingHref } from "./parts";
import { sanitizeSite } from "./safeUrl";
import "./site.css";

type Scheme = "light" | "dark";

const THEME_CLASS = { LIGHT: "theme-light", DARK: "theme-dark", AUTO: "theme-auto" } as const;
const SCHEME_KEY = "rzsite.scheme";

/** Preferência do visitante no tema "Ambos". Storage pode falhar (aba anônima): aí só não lembra. */
function readScheme(): Scheme | null {
  try {
    const v = localStorage.getItem(SCHEME_KEY);
    return v === "light" || v === "dark" ? v : null;
  } catch {
    return null;
  }
}
function saveScheme(s: Scheme) {
  try {
    localStorage.setItem(SCHEME_KEY, s);
  } catch {
    /* sem storage, a escolha vale só para esta visita */
  }
}
const systemScheme = (): Scheme =>
  window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";

/**
 * Meta description e dados estruturados (schema.org) para a busca local.
 *
 * É o que faz o Google mostrar endereço, horário e telefone direto no resultado de
 * "barbearia perto de mim" — um diferencial que o dono não teria como montar sozinho.
 */
function useSeo(site: PublicSite | null) {
  useEffect(() => {
    if (!site) return;
    document.title = site.barbershopName;

    const description =
      site.subheadline ||
      site.aboutText?.slice(0, 155) ||
      `${site.barbershopName}: agende seu horário pelo WhatsApp.`;
    let meta = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    const criouMeta = !meta;
    if (!meta) {
      meta = document.createElement("meta");
      meta.name = "description";
      document.head.appendChild(meta);
    }
    const descricaoAnterior = meta.content;
    meta.content = description;

    const ld: Record<string, unknown> = {
      "@context": "https://schema.org",
      "@type": "HairSalon",
      name: site.barbershopName,
      description,
      url: window.location.href,
    };
    if (site.logoUrl) ld.logo = site.logoUrl;
    if (site.heroImageUrl || site.gallery.length) ld.image = [site.heroImageUrl, ...site.gallery].filter(Boolean);
    if (site.addressText) ld.address = site.addressText.replace(/\s*\n\s*/g, ", ");
    if (site.whatsappNumber) ld.telephone = site.whatsappNumber;
    if (site.mapsUrl) ld.hasMap = site.mapsUrl;
    if (site.instagramUrl) ld.sameAs = [site.instagramUrl];
    const comPreco = site.services.filter((s) => s.price != null).map((s) => s.price!);
    if (comPreco.length) ld.priceRange = `R$ ${Math.min(...comPreco)} - R$ ${Math.max(...comPreco)}`;
    const reservar = bookingHref(site);
    // Com agendamento online o alvo é a página própria (absoluta: o Google não resolve relativo).
    if (reservar) ld.potentialAction = { "@type": "ReserveAction", target: new URL(reservar, window.location.origin).href };

    const script = document.createElement("script");
    script.type = "application/ld+json";
    script.textContent = JSON.stringify(ld);
    document.head.appendChild(script);

    return () => {
      script.remove();
      if (criouMeta) meta!.remove();
      else meta!.content = descricaoAnterior;
    };
  }, [site]);
}

/** Botão de alternância — só existe no tema "Ambos", como o editor promete. */
function SchemeToggle({ scheme, onChange }: { scheme: Scheme; onChange: (s: Scheme) => void }) {
  const next: Scheme = scheme === "dark" ? "light" : "dark";
  return (
    <button
      type="button"
      className="rz-scheme-toggle"
      onClick={() => onChange(next)}
      aria-label={next === "dark" ? "Mudar para tema escuro" : "Mudar para tema claro"}
      title={next === "dark" ? "Tema escuro" : "Tema claro"}
    >
      <span aria-hidden="true">{scheme === "dark" ? "☀" : "☾"}</span>
    </button>
  );
}

/**
 * O site renderizado: casca `.rzsite` (tema + cor do dono), o template e o botão de tema.
 *
 * Separado da página para ser reaproveitado pela prévia ao vivo do editor (/site-previa), que
 * desenha o RASCUNHO com o mesmo componente do site publicado — prévia que usa outro código
 * mente. Quem chama já deve ter passado o site pelo `sanitizeSite`.
 */
export function SiteView({
  site,
  templateOverride,
  themeOverride,
}: {
  site: PublicSite;
  /** Modelo para comparar sem salvar (?template= ou prévia do editor). */
  templateOverride?: SiteTemplate;
  themeOverride?: SiteTheme;
}) {
  const [scheme, setScheme] = useState<Scheme | null>(readScheme);

  const templateId = templateOverride && templateOverride in TEMPLATES ? templateOverride : site.template;
  const theme = themeOverride ?? site.theme;
  const Template = TEMPLATES[templateId] ?? TEMPLATES.CLASSICO;
  const auto = theme === "AUTO" || !THEME_CLASS[theme];
  // No "Ambos", a escolha do visitante (se houver) vence a do aparelho.
  const themeClass = auto
    ? scheme
      ? `theme-${scheme}`
      : "theme-auto"
    : THEME_CLASS[theme];

  return (
    <div
      className={`rzsite ${themeClass}`}
      // A única cor do dono entra aqui; o resto da paleta o CSS deriva.
      style={{ ["--accent" as string]: site.accentColor }}
    >
      <Template site={site} />
      {auto && (
        <SchemeToggle
          scheme={scheme ?? systemScheme()}
          onChange={(s) => {
            setScheme(s);
            saveScheme(s);
          }}
        />
      )}
    </div>
  );
}

export function SitePage() {
  const { slug = "" } = useParams();
  const [site, setSite] = useState<PublicSite | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Prévia por endereço (?template=VITRINE&tema=escuro): o dono compara os três modelos com o
  // próprio conteúdo antes de salvar. Só muda a apresentação — o conteúdo é o publicado.
  const [query] = useSearchParams();
  const previewTemplate = query.get("template")?.toUpperCase() as SiteTemplate | undefined;
  const previewTheme = ({ claro: "LIGHT", escuro: "DARK", ambos: "AUTO" } as Record<string, SiteTheme>)[
    query.get("tema")?.toLowerCase() ?? ""
  ];

  useEffect(() => {
    let vivo = true;
    api
      .get<PublicSite>(`/api/site/${encodeURIComponent(slug)}`)
      // Toda URL do dono passa pela barreira antes de chegar a qualquer template (ver safeUrl.ts).
      .then((s) => vivo && setSite(sanitizeSite(s)))
      .catch((e) => vivo && setError(e instanceof ApiError ? e.message : "Não foi possível carregar."));
    return () => {
      vivo = false;
    };
  }, [slug]);

  useSeo(site);

  if (error) {
    return (
      <div className="rzsite theme-auto" style={{ display: "grid", placeItems: "center", padding: 40 }}>
        <div style={{ textAlign: "center", maxWidth: "42ch" }}>
          <h1 className="rz-h2">Página não encontrada</h1>
          <p className="rz-lead">Confira o endereço. Se você é o dono, publique o site em Configurações.</p>
        </div>
      </div>
    );
  }

  // Sem esqueleto aqui de propósito: um site em branco por meio segundo é melhor que um
  // esqueleto que pisca com a cor errada — o acento e o tema só chegam junto com os dados.
  if (!site) return <div className="rzsite theme-auto" style={{ minHeight: "100vh" }} />;

  return <SiteView site={site} templateOverride={previewTemplate} themeOverride={previewTheme} />;
}
