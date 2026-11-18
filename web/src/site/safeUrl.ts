// Barreira de URL do site white-label.
//
// O site roda na MESMA origem do painel (/b/:slug). Um `javascript:` salvo como link do Instagram
// viraria um href executável com o token de quem estiver logado neste navegador. O backend já
// recusa isso ao salvar (SiteUrlPolicy), mas dado antigo, gravado antes da regra, ainda pode
// existir — então a página também filtra, e TODO href/src/url() do site passa por aqui.
import type { PublicSite } from "../api/types";

/**
 * Foto enviada pelo painel (V45), servida por nós: `/api/site/img/<uuid>`. É a ÚNICA URL
 * relativa aceita — o padrão é exato (prefixo fixo + UUID minúsculo, nada antes nem depois),
 * igual ao do backend (SiteUrlPolicy), então não passa `../`, query, `//host` nem outra rota.
 */
export const OWN_IMAGE = /^\/api\/site\/img\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** Caminho público de uma foto enviada. */
export const ownImageUrl = (id: string) => `/api/site/img/${id}`;

/**
 * A URL normalizada se for http(s) absoluta ou uma foto nossa (`/api/site/img/<uuid>`); senão
 * `null`.
 *
 * Também escapa `(`, `)` e `'`, que a normalização do navegador deixa passar: a capa vai num
 * `url(...)` de CSS, e um parêntese ali fecharia a função. (A foto nossa não tem nenhum deles —
 * o padrão só deixa passar hexadecimal e hífen.)
 */
export function safeHttpUrl(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (OWN_IMAGE.test(trimmed)) return trimmed;
  let u: URL;
  try {
    // Sem base de propósito: qualquer outro relativo lança e cai no `null`.
    u = new URL(trimmed);
  } catch {
    return null;
  }
  if (u.protocol !== "https:" && u.protocol !== "http:") return null;
  if (!u.hostname || u.username || u.password) return null;
  return u.href.replace(/\(/g, "%28").replace(/\)/g, "%29").replace(/'/g, "%27");
}

/**
 * O site com todos os campos de URL já filtrados. Aplicado uma vez, ao carregar: assim nenhum
 * template (nem uma seção nova) consegue esquecer o filtro.
 */
export function sanitizeSite(site: PublicSite): PublicSite {
  return {
    ...site,
    logoUrl: safeHttpUrl(site.logoUrl),
    heroImageUrl: safeHttpUrl(site.heroImageUrl),
    instagramUrl: safeHttpUrl(site.instagramUrl),
    mapsUrl: safeHttpUrl(site.mapsUrl),
    gallery: (site.gallery ?? []).map(safeHttpUrl).filter((u): u is string => !!u),
  };
}
