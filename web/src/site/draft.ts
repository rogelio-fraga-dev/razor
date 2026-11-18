// Rascunho do site: o formato que o editor edita e que a prévia ao vivo desenha.
//
// O editor (em Configurações) manda o rascunho para a prévia (/site-previa, num iframe da MESMA
// origem) por postMessage a cada mudança. A prévia junta o rascunho aos dados reais da barbearia
// (serviços, equipe, planos — vindos de GET /api/admin/site/preview) e desenha com o MESMO
// SiteView do site publicado. Prévia com outro código mentiria sobre o resultado.
import type { PublicSite, SiteContent, SiteTemplate, SiteTheme } from "../api/types";
import { sanitizeSite } from "./safeUrl";

/** Mensagens trocadas entre editor e prévia. O `type` é o protocolo — nada mais é aceito. */
export const DRAFT_MSG = "rz-site-draft";
export const READY_MSG = "rz-site-preview-ready";

export interface SiteDraft {
  templateId: SiteTemplate;
  themeMode: SiteTheme;
  accentColor: string;
  headline: string;
  subheadline: string;
  aboutText: string;
  logoUrl: string;
  heroImageUrl: string;
  gallery: string[];
  whatsappNumber: string;
  instagramUrl: string;
  mapsUrl: string;
  showTeam: boolean;
  showPlans: boolean;
  showPrices: boolean;
  onlineBookingEnabled: boolean;
  content: SiteContent;
}

const HEX = /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/;
const TEMPLATES: SiteTemplate[] = ["CLASSICO", "ESTUDIO", "VITRINE"];
const THEMES: SiteTheme[] = ["LIGHT", "DARK", "AUTO"];

const str = (v: unknown): string => (typeof v === "string" ? v : "");
const orNull = (v: unknown): string | null => {
  const s = str(v).trim();
  return s ? s : null;
};

/**
 * O rascunho por cima do site real, já pela barreira de URL.
 *
 * Mesmo vindo da própria origem, a mensagem é tratada como dado não confiável: cada campo é
 * conferido pelo tipo e as URLs passam pelo `sanitizeSite`, como no site publicado.
 */
export function applyDraft(base: PublicSite, raw: unknown): PublicSite {
  const d = (raw && typeof raw === "object" ? raw : {}) as Partial<Record<keyof SiteDraft, unknown>>;
  const template = TEMPLATES.includes(d.templateId as SiteTemplate) ? (d.templateId as SiteTemplate) : base.template;
  const theme = THEMES.includes(d.themeMode as SiteTheme) ? (d.themeMode as SiteTheme) : base.theme;
  const accent = HEX.test(str(d.accentColor)) ? str(d.accentColor) : base.accentColor;
  const gallery = Array.isArray(d.gallery) ? d.gallery.filter((g): g is string => typeof g === "string") : base.gallery;
  const showPrices = d.showPrices !== false;
  const content = d.content && typeof d.content === "object" ? (d.content as SiteContent) : base.content ?? null;

  return sanitizeSite({
    ...base,
    template,
    theme,
    accentColor: accent,
    headline: orNull(d.headline),
    subheadline: orNull(d.subheadline),
    aboutText: orNull(d.aboutText),
    logoUrl: orNull(d.logoUrl),
    heroImageUrl: orNull(d.heroImageUrl),
    gallery: gallery.slice(0, 12),
    whatsappNumber: orNull(d.whatsappNumber),
    instagramUrl: orNull(d.instagramUrl),
    mapsUrl: orNull(d.mapsUrl),
    // A base veio "completa" (?completo=true): os interruptores do RASCUNHO decidem aqui.
    services: base.services.map((s) => (showPrices ? s : { ...s, price: null })),
    team: d.showTeam === false ? [] : base.team,
    plans: d.showPlans === false ? [] : base.plans,
    onlineBooking: d.onlineBookingEnabled === true,
    content,
  });
}
