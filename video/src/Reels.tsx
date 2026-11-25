// Reels vertical (1080x1920) para captar as 5 barbearias do piloto, com a voz do dono.
// Voz: public/reels/voz1..5.wav (cortadas de roteiros/Barbearia1..5.m4a). Telas: bot-raw.mp4
// (gravação do iPhone, convertida de HDR) e os clipes do painel gravados por reels/record.mjs.
import type { ReactNode } from "react";
import { AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { loadFont as loadArchivo } from "@remotion/google-fonts/Archivo";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import { Backdrop, C, FONT_BODY, FONT_DISPLAY, FONT_MONO, Logo } from "./brand";

loadArchivo("normal", { weights: ["700", "800", "900"], subsets: ["latin"] });
loadInter("normal", { weights: ["500", "700", "800"], subsets: ["latin"] });
loadMono("normal", { weights: ["500", "700"], subsets: ["latin"] });

const FPS = 30;
const s2f = (s: number) => Math.round(s * FPS);

// Falas: duração do arquivo cortado e o início de cada palavra (s, relativo ao arquivo, da transcrição).
const LINES: { len: number; words: [string, number][] }[] = [
  { len: 4.17, words: [["Barbeiro,", 0.12], ["quantos", 0.82], ["clientes", 1.2], ["você", 1.58], ["perdeu", 1.74], ["hoje", 2.14],
    ["porque", 2.34], ["não", 2.54], ["deu", 2.7], ["tempo", 2.84], ["de", 3.04], ["responder", 3.16], ["o", 3.42], ["WhatsApp?", 3.62]] },
  { len: 6.87, words: [["Você", 0.1], ["tá", 0.3], ["com", 0.38], ["a", 0.52], ["máquina", 0.6], ["na", 0.84], ["mão,", 1.0], ["o", 1.58],
    ["cliente", 1.62], ["chama…", 2.06], ["e", 2.34], ["vai", 3.1], ["cortar", 3.4], ["em", 3.6], ["outro", 3.8], ["lugar.", 3.94],
    ["Fora", 4.78], ["o", 5.08], ["horário", 5.16], ["que", 5.38], ["fura", 5.52], ["e", 5.86], ["a", 5.96], ["cadeira", 6.0], ["parada.", 6.24]] },
  { len: 13.11, words: [["Eu", 0.1], ["criei", 0.24], ["o", 0.56], ["Razor.", 0.62], ["Ele", 1.28], ["responde", 1.38], ["seus", 1.74],
    ["clientes", 1.86], ["no", 2.18], ["WhatsApp,", 2.28], ["entende", 3.04], ["até", 3.46], ["áudio,", 3.8], ["marca", 4.28],
    ["com", 4.52], ["o", 4.56], ["barbeiro", 4.74], ["certo", 5.18], ["e", 5.44], ["cobra", 5.8], ["o", 6.04], ["sinal", 6.14], ["no", 6.28],
    ["PIX.", 6.38], ["E", 7.08], ["no", 7.14], ["painel", 7.22], ["você", 7.54], ["vê", 7.72], ["a", 7.92], ["agenda,", 8.04],
    ["fecha", 8.64], ["a", 8.86], ["comanda,", 8.98], ["confere", 9.58], ["o", 9.88], ["caixa", 9.98], ["e", 10.34], ["chama", 10.7],
    ["de", 10.92], ["volta", 11.08], ["quem", 11.28], ["sumiu.", 11.44], ["Tudo", 12.08], ["num", 12.2], ["lugar", 12.4], ["só.", 12.64]] },
  { len: 4.63, words: [["Tô", 0.1], ["procurando", 0.28], ["cinco", 0.72], ["barbearias", 0.96], ["pra", 1.52], ["testar", 1.74], ["de", 2.08],
    ["graça,", 2.22], ["comigo", 2.84], ["do", 3.06], ["lado", 3.26], ["ajudando", 3.46], ["a", 3.82], ["configurar.", 3.9]] },
  { len: 2.51, words: [["Quer", 0.1], ["ser", 0.2], ["uma", 0.34], ["delas?", 0.54], ["Comenta", 1.14], ["EU", 1.36], ["QUERO", 1.5],
    ["que", 1.74], ["eu", 1.9], ["te", 2.0], ["chamo.", 2.1]] },
];
const LEAD = 0.25;
const GAP = 0.3;
const END_HOLD = 2.6;
const STARTS = LINES.reduce<number[]>((a, l, i) => [...a, i === 0 ? LEAD : a[i - 1] + LINES[i - 1].len + GAP], []);
const S = STARTS.map(s2f); // início de cada fala em quadros
export const REELS_FRAMES = s2f(STARTS[4] + LINES[4].len + END_HOLD);

// ------------------------------------------------------------------ legendas estilo CapCut
function Captions() {
  const f = useCurrentFrame();
  const t = f / FPS;
  let li = -1;
  for (let i = 0; i < LINES.length; i++) if (t >= STARTS[i] - 0.05 && t < STARTS[i] + LINES[i].len + 0.2) li = i;
  if (li < 0) return null;
  const local = t - STARTS[li];
  const words = LINES[li].words;
  // Grupos de até 3 palavras; quebra também na pontuação.
  const groups: number[][] = [];
  let cur: number[] = [];
  words.forEach(([w], i) => {
    cur.push(i);
    if (cur.length === 3 || /[.,?!…]$/.test(w)) { groups.push(cur); cur = []; }
  });
  if (cur.length) groups.push(cur);
  let wi = 0;
  for (let i = 0; i < words.length; i++) if (local >= words[i][1] - 0.04) wi = i;
  const g = groups.find((gr) => gr.includes(wi))!;
  const gStart = words[g[0]][1];
  const pop = spring({ frame: s2f(local - gStart), fps: FPS, config: { damping: 14, mass: 0.5 } });
  return (
    <div style={{ position: "absolute", left: 60, right: 60, top: 1560, display: "flex", justifyContent: "center", flexWrap: "wrap",
      gap: "0 22px", transform: `scale(${0.86 + pop * 0.14})` }}>
      {g.map((i) => {
        const on = i === wi;
        const said = i <= wi;
        return (
          <span key={i} style={{
            fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 84, lineHeight: 1.08, letterSpacing: "-0.02em",
            textTransform: "uppercase", color: on ? C.edge : said ? "#fff" : "rgba(255,255,255,.55)",
            WebkitTextStroke: "3px #000", paintOrder: "stroke",
            textShadow: "0 6px 0 rgba(0,0,0,.55), 0 0 40px rgba(0,0,0,.6)",
          }}>{words[i][0].replace(/[,.]$/, "")}</span>
        );
      })}
    </div>
  );
}

// ------------------------------------------------------------------ peças visuais
function Phone({ children, top = 180, height = 1330 }: { children: ReactNode; top?: number; height?: number }) {
  const width = (height * 1080) / 2340;
  return (
    <div style={{ position: "absolute", top, left: (1080 - width - 24) / 2, width: width + 24, height: height + 24, padding: 12,
      borderRadius: 74, background: "linear-gradient(160deg,#2c2f38,#0b0c10)",
      boxShadow: "0 50px 120px rgba(0,0,0,.7), 0 0 0 2px rgba(255,255,255,.07) inset, 0 0 90px rgba(62,99,255,.25)" }}>
      <div style={{ width, height, borderRadius: 62, overflow: "hidden", position: "relative", background: "#000" }}>{children}</div>
    </div>
  );
}

/** Trecho de um vídeo [de, até] (s) tocado dentro de `dur` segundos (acelera o que precisar). */
function Clip({ src, from, to, dur }: { src: string; from: number; to: number; dur: number }) {
  const rate = (to - from) / dur;
  return (
    <OffthreadVideo src={staticFile(src)} startFrom={s2f(from)} playbackRate={rate} muted
      style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover" }} />
  );
}

function Chip({ text, tone = C.voltage }: { text: string; tone?: string }) {
  const f = useCurrentFrame();
  const s = spring({ frame: f, fps: FPS, config: { damping: 15, mass: 0.6 } });
  return (
    <div style={{ position: "absolute", top: 70, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
      <div style={{ transform: `translateY(${(1 - s) * -40}px) scale(${0.8 + s * 0.2})`, opacity: s, padding: "16px 34px",
        borderRadius: 999, background: "rgba(10,10,12,.88)", border: `2px solid ${tone}`, boxShadow: `0 0 50px ${tone}55`,
        fontFamily: FONT_MONO, fontWeight: 700, fontSize: 40, letterSpacing: "0.06em", color: C.chrome, textTransform: "uppercase" }}>
        {text}
      </div>
    </div>
  );
}

const pop = (f: number, delay = 0, damping = 13) => spring({ frame: f - delay, fps: FPS, config: { damping, mass: 0.6 } });

// ------------------------------------------------------------------ fala 1: o gancho
const ASKS = ["tem horário hoje?", "oi, tá aberto?", "consegue às 18h?", "quanto é o corte?", "tem vaga amanhã?",
  "???", "alguém aí?", "tem horário hoje?", "barba também?", "oi", "e aí, tem vaga?", "responde aí 🙏"];
function Hook() {
  const f = useCurrentFrame();
  const count = Math.min(ASKS.length, Math.floor(f / 7) + 1);
  return (
    <AbsoluteFill>
      <Backdrop />
      <div style={{ position: "absolute", top: 120, left: 70, right: 70 }}>
        {ASKS.slice(0, count).map((a, i) => {
          const p = pop(f, i * 7, 12);
          const left = i % 2 === 0;
          return (
            <div key={i} style={{ display: "flex", justifyContent: left ? "flex-start" : "flex-end", marginBottom: 18,
              transform: `translateX(${(1 - p) * (left ? -60 : 60)}px) scale(${0.7 + p * 0.3})`, opacity: p * (1 - Math.max(0, count - i - 7) * 0.12) }}>
              <div style={{ padding: "18px 28px", borderRadius: 30, background: "#1f2229", color: "#e9ebef",
                fontFamily: FONT_BODY, fontWeight: 500, fontSize: 40, boxShadow: "0 10px 30px rgba(0,0,0,.4)" }}>
                {a}
                <span style={{ marginLeft: 14, fontSize: 24, color: C.steel }}>{`1${4 + (i % 5)}:${10 + i * 3}`}</span>
              </div>
            </div>
          );
        })}
      </div>
      <div style={{ position: "absolute", top: 70, right: 60, transform: `scale(${1 + Math.sin(f / 3) * 0.04})` }}>
        <div style={{ minWidth: 120, height: 120, padding: "0 26px", borderRadius: 60, background: "#ff3b30", color: "#fff",
          display: "flex", alignItems: "center", justifyContent: "center", fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 58,
          boxShadow: "0 0 60px rgba(255,59,48,.6)" }}>{count * 3}</div>
      </div>
    </AbsoluteFill>
  );
}

// ------------------------------------------------------------------ fala 2: a dor
function Beat({ big, small, tone, at, until }: { big: string; small: string; tone: string; at: number; until: number }) {
  const f = useCurrentFrame();
  if (f < s2f(at) || f >= s2f(until)) return null;
  const p = pop(f, s2f(at), 11);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 380 }}>
      <div style={{ transform: `scale(${0.6 + p * 0.4}) rotate(${(1 - p) * -6}deg)`, opacity: p, textAlign: "center" }}>
        <div style={{ fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 150, lineHeight: 0.95, letterSpacing: "-0.04em", color: tone }}>{big}</div>
        <div style={{ marginTop: 28, fontFamily: FONT_MONO, fontWeight: 700, fontSize: 40, letterSpacing: "0.12em", color: C.steel,
          textTransform: "uppercase" }}>{small}</div>
      </div>
    </AbsoluteFill>
  );
}
function Pain() {
  const red = "#ff4d4d";
  return (
    <AbsoluteFill>
      <Backdrop />
      <Beat at={0} until={1.55} big="MÁQUINA NA MÃO" small="sem tempo pro celular" tone={C.chrome} />
      <Beat at={1.55} until={3.05} big="VISTO." small="e ninguém respondeu" tone={C.steel} />
      <Beat at={3.05} until={4.7} big="FOI PRO OUTRO" small="cliente perdido" tone={red} />
      <Beat at={4.7} until={5.95} big="FUROU" small="o horário das 15h" tone={red} />
      <Beat at={5.95} until={9} big="R$ 0" small="cadeira parada" tone={red} />
    </AbsoluteFill>
  );
}

// ------------------------------------------------------------------ fala 3: a solução
type Shot = { at: number; until: number; src: string; from: number; to: number; chip: string; tone?: string };
const SHOTS: Shot[] = [
  { at: 1.2, until: 3.0, src: "reels/bot-raw.mp4", from: 5.4, to: 12.6, chip: "responde no WhatsApp", tone: C.signal },
  { at: 3.0, until: 4.25, src: "reels/bot-raw.mp4", from: 34.6, to: 37.4, chip: "entende áudio", tone: C.signal },
  { at: 4.25, until: 5.65, src: "reels/bot-raw.mp4", from: 76.8, to: 79.6, chip: "escolhe o barbeiro", tone: C.signal },
  { at: 5.65, until: 7.0, src: "reels/bot-raw.mp4", from: 85.3, to: 88.3, chip: "cobra o sinal no PIX", tone: C.signal },
  { at: 7.0, until: 8.55, src: "reels/agenda.mp4", from: 0.5, to: 3.1, chip: "agenda" },
  { at: 8.55, until: 9.5, src: "reels/comanda.mp4", from: 2.9, to: 4.3, chip: "comanda · pago" },
  { at: 9.5, until: 10.6, src: "reels/caixa.mp4", from: 0.3, to: 1.9, chip: "caixa do dia" },
  { at: 10.6, until: 11.95, src: "reels/retencao.mp4", from: 1.7, to: 3.9, chip: "quem sumiu volta" },
];
const EARLY = 4; // as cenas entram 4 quadros antes da fala
function Solution() {
  const f = useCurrentFrame() - EARLY;
  const t = f / FPS;
  const logoP = pop(f, 0, 12);
  const logoOut = interpolate(t, [1.0, 1.25], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const phoneIn = pop(f, s2f(1.05), 15);
  const allP = pop(f, s2f(11.95), 14);
  return (
    <AbsoluteFill>
      <Backdrop />
      {t < 1.3 && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 360, opacity: logoOut }}>
          <div style={{ transform: `scale(${0.5 + logoP * 0.5})` }}><Logo size={190} /></div>
        </AbsoluteFill>
      )}
      {t >= 1.05 && t < 11.95 && (
        <div style={{ position: "absolute", inset: 0, transform: `translateY(${(1 - phoneIn) * 500}px)`, opacity: phoneIn }}>
          <Phone>
            {SHOTS.map((s) => (
              <Sequence key={s.at} from={s2f(s.at) + EARLY} durationInFrames={s2f(s.until - s.at)}>
                <Clip src={s.src} from={s.from} to={s.to} dur={s.until - s.at} />
              </Sequence>
            ))}
          </Phone>
        </div>
      )}
      {SHOTS.map((s) => (
        <Sequence key={"c" + s.at} from={s2f(s.at) + EARLY} durationInFrames={s2f(s.until - s.at)}>
          <Chip text={s.chip} tone={s.tone} />
        </Sequence>
      ))}
      {t >= 11.95 && (
        // "Tudo num lugar só": as quatro telas do painel lado a lado.
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 380 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 430px)", gap: 34, transform: `scale(${0.7 + allP * 0.3})`, opacity: allP }}>
            {[["reels/agenda.mp4", 3.0], ["reels/comanda.mp4", 5.0], ["reels/caixa.mp4", 1.5], ["reels/painel.mp4", 1.0]].map(([src, at], i) => (
              <div key={i} style={{ height: 600, borderRadius: 36, overflow: "hidden", position: "relative",
                boxShadow: "0 30px 80px rgba(0,0,0,.6), 0 0 0 2px rgba(255,255,255,.08)" }}>
                <OffthreadVideo src={staticFile(src as string)} startFrom={s2f(at as number)} muted
                  style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }} />
              </div>
            ))}
          </div>
        </AbsoluteFill>
      )}
    </AbsoluteFill>
  );
}

// ------------------------------------------------------------------ fala 4: a oferta
function Offer() {
  const f = useCurrentFrame();
  const p = pop(f, 0, 12);
  return (
    <AbsoluteFill>
      <Backdrop />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 420 }}>
        <div style={{ transform: `scale(${0.5 + p * 0.5})`, opacity: p, textAlign: "center" }}>
          <div style={{ fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 380, lineHeight: 0.8, letterSpacing: "-0.06em",
            background: C.grad, WebkitBackgroundClip: "text", color: "transparent" }}>5</div>
          <div style={{ fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 120, letterSpacing: "-0.03em", color: C.chrome, marginTop: 10 }}>VAGAS</div>
        </div>
        <div style={{ display: "flex", gap: 22, marginTop: 50 }}>
          {[0, 1, 2, 3, 4].map((i) => {
            const q = pop(f, 14 + i * 5, 12);
            return <div key={i} style={{ width: 120, height: 120, borderRadius: 30, border: `4px solid ${C.edge}`,
              background: "rgba(55,226,255,.08)", transform: `scale(${q})`, display: "flex", alignItems: "center", justifyContent: "center",
              fontFamily: FONT_MONO, fontWeight: 700, fontSize: 40, color: C.edge }}>{i + 1}</div>;
          })}
        </div>
        <div style={{ marginTop: 50, opacity: pop(f, 45), fontFamily: FONT_MONO, fontWeight: 700, fontSize: 38, letterSpacing: "0.1em",
          color: C.steel, textTransform: "uppercase", textAlign: "center", lineHeight: 1.5 }}>
          teste grátis<br />eu configuro com você
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
}

// ------------------------------------------------------------------ fala 5: a chamada
function Cta() {
  const f = useCurrentFrame();
  const p = pop(f, s2f(1.0), 10);
  const pulse = 1 + Math.max(0, Math.sin((f - 40) / 6)) * 0.035 * (f > 40 ? 1 : 0);
  return (
    <AbsoluteFill>
      <Backdrop />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 300 }}>
        <div style={{ fontFamily: FONT_DISPLAY, fontWeight: 800, fontSize: 90, color: C.chrome, opacity: pop(f, 0), letterSpacing: "-0.02em" }}>
          Comenta
        </div>
        <div style={{ marginTop: 30, transform: `scale(${(0.4 + p * 0.6) * pulse})`, opacity: p, padding: "38px 70px", borderRadius: 40,
          background: C.grad, boxShadow: "0 30px 120px rgba(62,99,255,.6)", fontFamily: FONT_DISPLAY, fontWeight: 900, fontSize: 150,
          letterSpacing: "-0.03em", color: "#fff" }}>
          EU QUERO
        </div>
        <div style={{ marginTop: 70, opacity: pop(f, s2f(1.8)) }}><Logo size={110} /></div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
}

// ------------------------------------------------------------------ montagem
export function Reels() {
  const { durationInFrames } = useVideoConfig();
  const scenes = [Hook, Pain, Solution, Offer, Cta];
  return (
    <AbsoluteFill style={{ background: C.obsidian }}>
      {/* Jimmy Blythe, "Armour Avenue Struggle" (1924): domínio público. */}
      <Audio src={staticFile("music/blues-bed.wav")} startFrom={s2f(20)}
        volume={(f) => {
          const end = s2f(STARTS[4] + LINES[4].len);
          const base = f > end ? interpolate(f, [end, end + 12], [0.08, 0.2], { extrapolateRight: "clamp" }) : 0.08;
          return base * interpolate(f, [0, 8], [0, 1], { extrapolateRight: "clamp" })
            * interpolate(f, [durationInFrames - 25, durationInFrames], [1, 0], { extrapolateLeft: "clamp" });
        }} />
      {scenes.map((Scene, i) => {
        const from = i === 0 ? 0 : S[i] - EARLY;
        const to = i < 4 ? S[i + 1] - EARLY : durationInFrames;
        return (
          <Sequence key={i} from={from} durationInFrames={to - from}>
            <Scene />
          </Sequence>
        );
      })}
      {LINES.map((_, i) => (
        <Sequence key={"v" + i} from={S[i]}>
          <Audio src={staticFile(`reels/voz${i + 1}.wav`)} />
        </Sequence>
      ))}
      <Captions />
    </AbsoluteFill>
  );
}
