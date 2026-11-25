// Peças visuais da marca usadas nos vídeos: cores, logo, legenda, selo de capítulo e moldura.
import type { ReactNode } from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";

export const C = {
  obsidian: "#0A0A0C",
  graphite: "#141519",
  chrome: "#E9EBEF",
  steel: "#8B93A1",
  voltage: "#3E63FF",
  edge: "#37E2FF",
  signal: "#25D366",
  grad: "linear-gradient(68deg, #3E63FF 0%, #37E2FF 100%)",
};

export const FONT_DISPLAY = "'Archivo', 'Segoe UI', Arial, sans-serif";
export const FONT_BODY = "'Inter', 'Segoe UI', Arial, sans-serif";
export const FONT_MONO = "'JetBrains Mono', Consolas, monospace";

export function Logo({ size = 64 }: { size?: number }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: size * 0.3 }}>
      <svg viewBox="0 0 100 100" width={size} height={size}>
        <defs>
          <linearGradient id="lg" x1="0" y1="1" x2="1" y2="0">
            <stop offset="0" stopColor="#3E63FF" />
            <stop offset="1" stopColor="#37E2FF" />
          </linearGradient>
        </defs>
        <polygon points="30,92 52,92 84,8 62,8" fill={C.chrome} />
        <polygon points="62,8 84,8 81,16 59,16" fill="url(#lg)" />
      </svg>
      <span style={{ fontFamily: FONT_DISPLAY, fontWeight: 800, fontSize: size * 0.62, letterSpacing: "0.02em", color: C.chrome }}>
        RAZOR
      </span>
    </div>
  );
}

/** Fundo da marca: obsidiana com brilho azul suave que respira devagar. */
export function Backdrop({ children }: { children?: ReactNode }) {
  const f = useCurrentFrame();
  const x = 50 + Math.sin(f / 90) * 8;
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        background: `radial-gradient(1100px 620px at ${x}% 0%, rgba(62,99,255,.28), transparent 70%),
                     radial-gradient(900px 500px at 90% 110%, rgba(55,226,255,.14), transparent 70%), ${C.obsidian}`,
      }}
    >
      <div
        style={{
          position: "absolute",
          inset: 0,
          backgroundImage:
            "linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.035) 1px, transparent 1px)",
          backgroundSize: "72px 72px",
          maskImage: "radial-gradient(ellipse at center, #000 30%, transparent 75%)",
        }}
      />
      {children}
    </div>
  );
}

/** Legenda: a frase atual em pílula escura, embaixo e ao centro. */
export function Caption({ text, bottom = 54, size = 34, left = 0 }: { text: string; bottom?: number; size?: number; left?: number }) {
  if (!text) return null;
  return (
    <div style={{ position: "absolute", left, right: 0, bottom, display: "flex", justifyContent: "center", pointerEvents: "none" }}>
      <div
        style={{
          maxWidth: 1500,
          padding: "14px 28px",
          borderRadius: 18,
          background: "rgba(10,10,12,.82)",
          border: "1px solid rgba(255,255,255,.1)",
          boxShadow: "0 20px 60px rgba(0,0,0,.5)",
          color: C.chrome,
          fontFamily: FONT_BODY,
          fontWeight: 600,
          fontSize: size,
          lineHeight: 1.3,
          textAlign: "center",
          textWrap: "balance",
        }}
      >
        {text}
      </div>
    </div>
  );
}

/** Selo do capítulo: entra pela esquerda sobre a tela escurecida e some depois de ~2,6 s. */
export function ChapterTag({ label }: { label: string }) {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  if (!label) return null;
  const inS = spring({ frame: f, fps, config: { damping: 18, mass: 0.7 } });
  const out = interpolate(f, [fps * 2.3, fps * 2.8], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  if (out <= 0) return null;
  const [num, ...rest] = label.split(" · ");
  return (
    <>
      <div style={{ position: "absolute", inset: 0, background: "rgba(10,10,12,.62)", opacity: out * Math.min(1, f / 6) }} />
      <div
        style={{
          position: "absolute",
          left: 120,
          top: 440,
          opacity: out,
          transform: `translateX(${(1 - inS) * -120}%) translateY(${(1 - out) * -20}px)`,
          display: "flex",
          alignItems: "center",
          gap: 26,
          padding: "26px 40px",
          borderRadius: 24,
          background: "rgba(10,10,12,.92)",
          border: "1px solid rgba(62,99,255,.55)",
          boxShadow: "0 30px 80px rgba(0,0,0,.6), 0 0 60px rgba(62,99,255,.18)",
        }}
      >
        <span style={{ fontFamily: FONT_MONO, fontWeight: 700, fontSize: 48, background: C.grad, WebkitBackgroundClip: "text", color: "transparent" }}>
          {num}
        </span>
        <span style={{ fontFamily: FONT_DISPLAY, fontWeight: 800, fontSize: 64, color: C.chrome, letterSpacing: "-0.02em" }}>
          {rest.join(" · ")}
        </span>
      </div>
    </>
  );
}

/** Moldura de celular para as cenas gravadas em tela vertical. */
export function PhoneFrame({ children, height = 940 }: { children: ReactNode; height?: number }) {
  const width = (height * 390) / 844;
  return (
    <div
      style={{
        width: width + 28,
        height: height + 28,
        padding: 14,
        borderRadius: 64,
        background: "linear-gradient(160deg, #2a2d35, #0d0e12)",
        boxShadow: "0 60px 140px rgba(0,0,0,.7), inset 0 0 0 2px rgba(255,255,255,.08)",
      }}
    >
      <div style={{ width, height, borderRadius: 50, overflow: "hidden", position: "relative", background: "#000" }}>{children}</div>
    </div>
  );
}
