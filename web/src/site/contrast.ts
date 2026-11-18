// Contraste da cor do dono — garante WCAG AA com QUALQUER acento escolhido no editor.
//
// O dono escolhe uma cor só (--accent). Ela aparece em dois papéis bem diferentes:
//   1. Como FUNDO (botão, faixa final, selo de preço): o texto por cima vira preto ou branco,
//      o que tiver mais contraste — nunca branco sobre amarelo-claro.
//   2. Como TEXTO/borda sobre o fundo da página (olho da seção, preço, link): aí a cor é
//      escurecida (tema claro) ou clareada (tema escuro) só o necessário para passar 4,5:1.
//      O tom continua o da marca; só a luminância anda.
//
// As paletas abaixo ESPELHAM os tokens de cada template em site.css (`--l-*` e `--d-*`). Se
// mudar uma cor lá, mude aqui — o teste de contraste (scratch) confere as duas pontas.

export type Rgb = [number, number, number];

/** Fundos claros de todos os templates (página, faixa alternada e cartão). */
export const LIGHT_BGS = [
  "#F5EFE4", "#ECE3D3", "#FFFCF6", // Clássico
  "#F7F6F2", "#EEECE6", "#FFFFFF", // Estúdio
  "#FFFFFF", "#F2F2EF", // Vitrine
];
/** Fundos escuros de todos os templates. */
export const DARK_BGS = [
  "#13100C", "#1B1712", "#221D17", // Clássico
  "#0A0A0A", "#121212", "#161616", // Estúdio
  "#09090B", "#131316", "#18181C", // Vitrine
];

/** Texto sobre o acento: os dois candidatos (quase-preto e branco). */
export const INK_DARK = "#0B0B0C";
export const INK_LIGHT = "#FFFFFF";

export function parseHex(hex: string | null | undefined): Rgb | null {
  if (!hex) return null;
  let h = hex.trim().replace(/^#/, "");
  if (h.length === 3) h = h.split("").map((c) => c + c).join("");
  if (!/^[0-9a-f]{6}$/i.test(h)) return null;
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

export const toHex = ([r, g, b]: Rgb) =>
  "#" + [r, g, b].map((v) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, "0")).join("").toUpperCase();

/** Luminância relativa (WCAG 2.x). */
export function luminance([r, g, b]: Rgb): number {
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

export function contrast(a: Rgb, b: Rgb): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** Mistura em sRGB — o mesmo que `color-mix(in srgb, a p%, b)` e que a composição de opacidade. */
export const mix = (a: Rgb, b: Rgb, pA: number): Rgb => [
  a[0] * pA + b[0] * (1 - pA),
  a[1] * pA + b[1] * (1 - pA),
  a[2] * pA + b[2] * (1 - pA),
];

const minContrast = (c: Rgb, bgs: Rgb[]) => Math.min(...bgs.map((b) => contrast(c, b)));

/**
 * O acento ajustado para ser lido sobre os fundos dados: anda em direção a `toward` (preto no
 * tema claro, branco no escuro) em passos de 2% até passar `target` contra o PIOR fundo.
 * Também confere o fundo levemente tingido pelo acento (chips e realces usam 14% dele).
 */
export function readableOn(accent: Rgb, bgs: Rgb[], toward: Rgb, target = 4.6): Rgb {
  const all = [...bgs, ...bgs.map((b) => mix(accent, b, 0.14))];
  for (let i = 0; i <= 50; i++) {
    const c = mix(toward, accent, i / 50);
    if (minContrast(c, all) >= target) return c;
  }
  return toward;
}

export interface AccentVars {
  /** O acento normalizado (e, só em cinzas médios, levemente ajustado para levar texto AA). */
  accent: string;
  /** Texto sobre o acento. */
  onAccent: string;
  /** Acento como texto no tema claro / escuro. */
  inkLight: string;
  inkDark: string;
  /** Contorno do botão cheio quando o acento quase some no fundo (preto no escuro, creme no claro). */
  edgeLight: string;
  edgeDark: string;
}

const FALLBACK = "#C9A227";

export function accentVars(hex: string | null | undefined): AccentVars {
  let accent = parseHex(hex) ?? parseHex(FALLBACK)!;
  const dark = parseHex(INK_DARK)!;
  const white = parseHex(INK_LIGHT)!;
  // Branco quando passa AA; senão o que tiver mais contraste.
  const cw = contrast(white, accent);
  const cd = contrast(dark, accent);
  const onAccent = cw >= 4.5 || cw >= cd ? INK_LIGHT : INK_DARK;
  // Numa faixa estreita de cinzas médios (ex.: #777777) nem preto nem branco chegam a 4,5:1.
  // Aí o acento usado como FUNDO anda um tiquinho para longe do texto até passar — a diferença
  // é imperceptível (#777777 vira #737373), e todo texto sobre a cor da marca continua AA.
  if (Math.max(cw, cd) < 4.5) {
    const away: Rgb = onAccent === INK_LIGHT ? [0, 0, 0] : [255, 255, 255];
    const on = onAccent === INK_LIGHT ? white : dark;
    for (let i = 1; i <= 50 && contrast(on, accent) < 4.5; i++) accent = mix(away, accent, 0.01);
  }
  const lightBgs = LIGHT_BGS.map((h) => parseHex(h)!);
  const darkBgs = DARK_BGS.map((h) => parseHex(h)!);
  const inkLight = readableOn(accent, lightBgs, [0, 0, 0]);
  const inkDark = readableOn(accent, darkBgs, [255, 255, 255]);
  // Contraste "de objeto" (WCAG 1.4.11): se o botão cheio quase some no fundo, ganha contorno.
  const edge = (bgs: Rgb[], ink: Rgb) => (minContrast(accent, bgs) < 1.8 ? toHex(ink) : "transparent");
  return {
    accent: toHex(accent),
    onAccent,
    inkLight: toHex(inkLight),
    inkDark: toHex(inkDark),
    edgeLight: edge(lightBgs, inkLight),
    edgeDark: edge(darkBgs, inkDark),
  };
}

/** As variáveis CSS que a página aplica no `.rzsite` (ver `SiteView`). */
export function accentStyle(hex: string | null | undefined): Record<string, string> {
  const v = accentVars(hex);
  return {
    "--accent": v.accent,
    "--on-accent": v.onAccent,
    "--accent-ink-l": v.inkLight,
    "--accent-ink-d": v.inkDark,
    "--accent-edge-l": v.edgeLight,
    "--accent-edge-d": v.edgeDark,
  };
}
