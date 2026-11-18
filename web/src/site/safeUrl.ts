// Barreira de URL do site white-label.
//
// O site roda na MESMA origem do painel (/b/:slug). Um `javascript:` salvo como link do Instagram
// viraria um href executável com o token de quem estiver logado neste navegador. O backend já
// recusa isso ao salvar (SiteUrlPolicy), mas dado antigo, gravado antes da regra, ainda pode
// existir — então a página também filtra, e TODO href/src/url() do site passa por aqui.
import type { PublicSite } from "../api/types";

/**
 * A URL normalizada se for http(s) absoluta; senão `null`.
 *
 * Também escapa `(`, `)` e `'`, que a normalização do navegador deixa passar: a capa vai num
 * `url(...)` de CSS, e um parêntese ali fecharia a função.
 */
export function safeHttpUrl(raw: string | null | undefined): string | null {
  if (!raw) return null;
  let u: URL;
  try {
    u = new URL(raw.trim());
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
