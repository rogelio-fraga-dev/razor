// Marca RAZOR (brand book v2): simbolo "The Edge" — a lamina reduzida a um
// paralelogramo a 68 graus, com o glint do gume em gradiente voltage→edge.

const GLINT_ID = "razor-glint";

/** Simbolo "The Edge". `color` controla a lamina (padrao: chrome). */
export function EdgeMark({ size = 26, color = "#E9EBEF" }: { size?: number; color?: string }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={GLINT_ID} x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" stopColor="#3E63FF" />
          <stop offset="1" stopColor="#37E2FF" />
        </linearGradient>
      </defs>
      <polygon points="30,92 52,92 84,8 62,8" fill={color} />
      <polygon points="62,8 84,8 81,16 59,16" fill={`url(#${GLINT_ID})`} />
    </svg>
  );
}

/** Lockup simbolo + wordmark, para sidebar e telas de auth. */
export function RazorLogo({ size = 26 }: { size?: number }) {
  return (
    <span className="brand-lockup">
      <EdgeMark size={size} />
      <span className="brand-word">RAZOR</span>
    </span>
  );
}
