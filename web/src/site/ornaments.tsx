// Peças-assinatura em 3D/SVG — uma por template, todas em CSS 3D + SVG puro (sem WebGL, sem lib).
//
//   BarberPole  (Clássico) — poste de barbeiro de verdade: cilindro de faces com a listra em hélice
//                            contínua, girando dentro do tubo de vidro. Para fora da tela.
//   Razor       (Estúdio)  — navalha em camadas (cabo de trás, lâmina, cabo da frente) que se abre
//                            com a rolagem da capa, com brilho correndo pelo fio.
//   PhotoRing   (Vitrine)  — anel 3D com as fotos da galeria, girando como uma vitrine giratória.
//   Scissors    (Clássico) — tesoura desenhada a traço quando entra na tela.
//
// Tudo é decorativo (aria-hidden): a informação nunca mora só aqui.
import { useId, type CSSProperties } from "react";
import { usePlayWhenVisible } from "./fx";

/* ---------------------------------------------------------------------------
   Poste de barbeiro.
   ------------------------------------------------------------------------- */

const POLE_FACES = 20;
const POLE_R = 26; // apótema do prisma (px)
const POLE_H = 236; // altura do vidro (px)

export function BarberPole() {
  const ref = usePlayWhenVisible<HTMLDivElement>();
  const w = 2 * POLE_R * Math.tan(Math.PI / POLE_FACES);
  const circ = w * POLE_FACES;
  // Período da listra ao longo da diagonal: a circunferência precisa conter um número inteiro de
  // períodos na horizontal (C·cos45° = n·P), senão a hélice "quebra" na emenda das faces.
  const period = (circ * Math.SQRT1_2) / 2;
  const faces = Array.from({ length: POLE_FACES }, (_, i) => i);
  return (
    <div
      ref={ref}
      className="rz-pole"
      aria-hidden="true"
      style={
        {
          "--pole-r": `${POLE_R}px`,
          "--pole-h": `${POLE_H}px`,
          "--pole-w": `${w + 0.6}px`,
          "--pole-c": `${circ}px`,
          "--pole-p": `${period}px`,
        } as CSSProperties
      }
    >
      <div className="rz-pole-cap rz-pole-cap-top">
        <span className="rz-pole-ball" />
      </div>
      <div className="rz-pole-glass">
        <div className="rz-pole-scene">
          <div className="rz-pole-cyl">
            {faces.map((i) => (
              <span
                key={i}
                className="rz-pole-face"
                style={{
                  transform: `rotateY(${(i * 360) / POLE_FACES}deg) translateZ(${POLE_R}px)`,
                  backgroundPosition: `${-i * w}px 0`,
                }}
              />
            ))}
          </div>
        </div>
        <span className="rz-pole-shade" />
      </div>
      <div className="rz-pole-cap rz-pole-cap-bottom" />
    </div>
  );
}

/* ---------------------------------------------------------------------------
   Navalha. viewBox 600×140, pivô em (300, 70) = centro: a lâmina gira em torno dele.
   ------------------------------------------------------------------------- */

const BLADE =
  "M284 62Q284 54 294 54L346 54L354 48Q452 43 548 50Q568 52 568 70Q568 96 548 104L362 104Q352 104 348 96L342 84Q330 80 318 82Q302 86 294 82Q284 78 284 70Z";
const SCALE = "M318 70Q318 50 298 48L62 40Q22 40 22 70Q22 100 62 100L298 92Q318 90 318 70Z";

export function Razor({ engraving }: { engraving: string }) {
  const uid = useId().replace(/:/g, "");
  const steel = `rzs-${uid}`;
  const horn = `rzh-${uid}`;
  const clip = `rzc-${uid}`;
  const shine = `rzg-${uid}`;
  const pin = `rzp-${uid}`;
  const txt = engraving.length > 22 ? engraving.slice(0, 22).trim() + "…" : engraving;
  return (
    <div className="rz-razor" aria-hidden="true">
      <div className="rz-razor-float">
        <div className="rz-razor-obj">
          {/* Cabo de trás */}
          <svg className="rz-razor-l rz-razor-back" viewBox="0 0 600 140">
            <defs>
              <linearGradient id={horn} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#4a4540" />
                <stop offset="0.45" stopColor="#1a1715" />
                <stop offset="1" stopColor="#0c0b0a" />
              </linearGradient>
            </defs>
            <path d={SCALE} fill={`url(#${horn})`} stroke="rgba(255,255,255,.18)" strokeWidth="1" />
          </svg>
          {/* Lâmina: gira no pivô */}
          <div className="rz-razor-blade-intro">
            <svg className="rz-razor-l rz-razor-blade" viewBox="0 0 600 140">
              <defs>
                <linearGradient id={steel} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stopColor="#f4f6f8" />
                  <stop offset="0.35" stopColor="#c3c9d0" />
                  <stop offset="0.7" stopColor="#7d858f" />
                  <stop offset="0.86" stopColor="#d9dde2" />
                  <stop offset="1" stopColor="#ffffff" />
                </linearGradient>
                <linearGradient id={shine} x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stopColor="#fff" stopOpacity="0" />
                  <stop offset="0.5" stopColor="#fff" stopOpacity="0.95" />
                  <stop offset="1" stopColor="#fff" stopOpacity="0" />
                </linearGradient>
                <clipPath id={clip}>
                  <path d={BLADE} />
                </clipPath>
              </defs>
              <path d={BLADE} fill={`url(#${steel})`} stroke="rgba(0,0,0,.35)" strokeWidth="0.8" />
              <path d="M354 49Q452 44 548 51" fill="none" stroke="#fff" strokeOpacity="0.85" strokeWidth="1.2" />
              <path d="M362 104L548 104Q562 99 565 88L561 88Q556 96 547 97L364 97Z" fill="#fff" fillOpacity="0.55" />
              <text
                x="452"
                y="80"
                textAnchor="middle"
                fontSize="11"
                letterSpacing="3"
                fill="#2b2f35"
                fillOpacity="0.45"
                style={{ fontFamily: "Georgia, serif", textTransform: "uppercase" }}
              >
                {txt}
              </text>
              <g clipPath={`url(#${clip})`}>
                <rect className="rz-razor-shine" x="200" y="30" width="70" height="90" fill={`url(#${shine})`} />
              </g>
            </svg>
          </div>
          {/* Cabo da frente, com filete na cor da marca e os pinos */}
          <svg className="rz-razor-l rz-razor-front" viewBox="0 0 600 140">
            <defs>
              <radialGradient id={pin} cx="0.35" cy="0.35" r="0.7">
                <stop offset="0" stopColor="#ffffff" />
                <stop offset="0.5" stopColor="#bfc4ca" />
                <stop offset="1" stopColor="#5d636b" />
              </radialGradient>
            </defs>
            <path d={SCALE} fill={`url(#${horn})`} stroke="rgba(255,255,255,.28)" strokeWidth="1" />
            <path d="M66 52L292 58" stroke="rgba(255,255,255,.22)" strokeWidth="1.4" fill="none" strokeLinecap="round" />
            <path d="M74 86L290 82" className="rz-razor-inlay" strokeWidth="1.6" fill="none" strokeLinecap="round" />
            <circle cx="300" cy="70" r="7.5" fill={`url(#${pin})`} />
            <circle cx="46" cy="70" r="6" fill={`url(#${pin})`} />
          </svg>
        </div>
      </div>
      <div className="rz-razor-shadow" />
    </div>
  );
}

/* ---------------------------------------------------------------------------
   Anel de fotos 3D (Vitrine).
   ------------------------------------------------------------------------- */

export function PhotoRing({ photos }: { photos: string[] }) {
  const ref = usePlayWhenVisible<HTMLDivElement>();
  const n = photos.length;
  const w = 168;
  const r = Math.round(w / 2 / Math.tan(Math.PI / n) + 26);
  return (
    <div ref={ref} className="rz-ring" aria-hidden="true" style={{ "--ring-r": `${r}px` } as CSSProperties}>
      <div className="rz-ring-tilt">
        <div className="rz-ring-spin">
          {photos.map((src, i) => (
            <figure key={src + i} className="rz-ring-card" style={{ transform: `rotateY(${(i * 360) / n}deg) translateZ(${r}px)` }}>
              <img src={src} alt="" loading="eager" decoding="async" />
            </figure>
          ))}
        </div>
      </div>
      <div className="rz-ring-floor" />
    </div>
  );
}

/* ---------------------------------------------------------------------------
   Tesoura desenhada a traço (Clássico) — anima quando o pai recebe `data-shown`.
   ------------------------------------------------------------------------- */

export function Scissors({ className = "" }: { className?: string }) {
  return (
    <svg className={`rz-scissors ${className}`} viewBox="0 0 120 100" aria-hidden="true">
      <circle pathLength={1} cx="22" cy="30" r="13" />
      <circle pathLength={1} cx="22" cy="70" r="13" />
      <path pathLength={1} d="M33 38C50 47 80 58 113 64C90 65 70 60 52 52" />
      <path pathLength={1} d="M33 62C50 53 80 42 113 36C90 35 70 40 52 48" />
      <circle className="rz-scissors-pin" cx="54" cy="50" r="2.4" />
    </svg>
  );
}

/** Pente desenhado a traço — par da tesoura nos ornamentos do Clássico. */
export function Comb({ className = "" }: { className?: string }) {
  const teeth = Array.from({ length: 13 }, (_, i) => 14 + i * 7);
  return (
    <svg className={`rz-scissors ${className}`} viewBox="0 0 120 100" aria-hidden="true">
      <path pathLength={1} d="M8 36H112V48H8Z" />
      <path pathLength={1} d={teeth.map((x, i) => `M${x} 48V${i % 2 ? 70 : 78}`).join("")} />
    </svg>
  );
}
