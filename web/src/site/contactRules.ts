// Validação amigável dos campos de contato e de link do editor do site.
//
// Espelha o backend (SiteUrlPolicy) para o dono descobrir o erro ENQUANTO digita, com uma frase
// que ele entende — e não um 400 depois de clicar em salvar. O backend continua sendo a regra:
// isto aqui só antecipa. Onde dá para consertar sozinho (faltou o https://, digitou @perfil,
// esqueceu o 55), o editor conserta em vez de reclamar.
import { OWN_IMAGE } from "./safeUrl";

const onlyDigits = (v: string) => v.replace(/\D/g, "");

/** WhatsApp só com dígitos e com o 55 do Brasil quando o dono digitou só DDD + número. */
export function normalizeWhatsapp(v: string): string {
  const d = onlyDigits(v);
  if (d.length === 10 || d.length === 11) return `55${d}`;
  return d;
}

export function whatsappError(v: string): string | null {
  if (!v.trim()) return null;
  if (/[a-zA-Z]/.test(v)) return "Use só números: DDD + número, ex.: 11 98765-4321.";
  const d = onlyDigits(v);
  if (d.length < 10) return "Faltam dígitos: coloque o DDD + número, ex.: 11 98765-4321.";
  if (d.length > 13) return "Número longo demais. Confira o DDD e o número.";
  return null;
}

const HANDLE = /^@?([A-Za-z0-9._]{1,30})$/;

/** Aceita @perfil, perfil, instagram.com/perfil — e devolve o link completo. */
export function normalizeInstagram(v: string): string {
  const s = v.trim();
  if (!s) return "";
  const h = HANDLE.exec(s);
  if (h && !s.includes("/") && !/\.(com|br)$/i.test(s)) return `https://instagram.com/${h[1]}`;
  if (/^(www\.)?instagram\.com\//i.test(s)) return `https://${s}`;
  return s;
}

export function instagramError(v: string): string | null {
  const s = normalizeInstagram(v);
  if (!s) return null;
  const u = parseHttps(s);
  if (!u) return "Cole o link do perfil (https://instagram.com/…) ou só o @perfil.";
  const host = u.hostname.toLowerCase();
  if (host !== "instagram.com" && !host.endsWith(".instagram.com")) return "O link precisa ser do instagram.com.";
  return null;
}

/** Faltou o https:// num link do Google Maps? Completa. */
export function normalizeMaps(v: string): string {
  const s = v.trim();
  if (!s) return "";
  if (/^(maps\.app\.goo\.gl|goo\.gl|(www\.)?google\.|maps\.google\.)/i.test(s)) return `https://${s}`;
  return s;
}

export function mapsError(v: string): string | null {
  const s = normalizeMaps(v);
  if (!s) return null;
  const u = parseHttps(s);
  if (!u) return "Cole o link completo do Google Maps (botão Compartilhar → Copiar link).";
  const host = u.hostname.toLowerCase();
  const path = u.pathname;
  const ok =
    host === "maps.app.goo.gl" ||
    /^maps\.google\.[a-z]{2,3}(\.[a-z]{2})?$/.test(host) ||
    (/^(www\.)?google\.[a-z]{2,3}(\.[a-z]{2})?$/.test(host) && path.startsWith("/maps")) ||
    (host === "goo.gl" && path.startsWith("/maps"));
  return ok ? null : "O link precisa ser do Google Maps (botão Compartilhar → Copiar link).";
}

/** Link de imagem colado à mão: https completo, ou uma foto já enviada. */
export function imageUrlError(v: string): string | null {
  const s = v.trim();
  if (!s || OWN_IMAGE.test(s)) return null;
  return parseHttps(s) ? null : "Use um endereço completo começando com https://";
}

function parseHttps(s: string): URL | null {
  if (/\s/.test(s) || s.length > 2048) return null;
  try {
    const u = new URL(s);
    return u.protocol === "https:" && u.hostname && !u.username && !u.password ? u : null;
  } catch {
    return null;
  }
}
