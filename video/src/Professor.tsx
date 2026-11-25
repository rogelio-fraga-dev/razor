// Vídeo de demonstração para o professor: o sistema por dentro (sem a conversa do bot).
// Cenas, narração e clipes vêm de professor/script.json, public/professor/voice/ (voz do dono,
// preparada por tools/prep-voice.py), public/professor/durations.json (a voz de IA, que deu o ritmo
// das gravações) e public/professor/clips.json (gerado por professor/record.mjs).
import { AbsoluteFill, Audio, OffthreadVideo, Sequence, Series, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { loadFont as loadArchivo } from "@remotion/google-fonts/Archivo";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import script from "../professor/script.json";
import ttsDurations from "../public/professor/durations.json";
import voiceDurations from "../public/professor/voice/durations.json";
import clips from "../public/professor/clips.json";
import { Backdrop, C, Caption, ChapterTag, FONT_BODY, FONT_DISPLAY, FONT_MONO, Logo, PhoneFrame } from "./brand";

loadArchivo("normal", { weights: ["700", "800"], subsets: ["latin"] });
loadInter("normal", { weights: ["400", "600"], subsets: ["latin"] });
loadMono("normal", { weights: ["500", "700"], subsets: ["latin"] });

export const FPS = 30;
const TRANSITION = 12;
const VOICE_DELAY = 0.35; // s antes da narração começar em cada cena
const TAIL = 1.1; // s de respiro depois da narração

type Scene = { id: string; chapter: string; text: string; card?: boolean; hold?: number; cuts?: [number, number][] };
const SCENES = script.scenes as Scene[];
const DUR = voiceDurations as Record<string, number>; // duração de cada fala gravada
const TTS = ttsDurations as Record<string, number>; // a gravação de tela foi feita no ritmo da voz de IA
const MAX_RATE = 1.3;
// Fundo musical desligado: o dono preferiu só a voz (25/11). A trilha continua em public/music.
const MUSIC = false; // quanto a gravação pode acelerar para acompanhar uma fala mais curta
const CLIPS = clips as Record<string, { file: string; offset: number; length: number }>;

// Quanto da gravação a cena precisa mostrar (o carimbo PAGO, o tema claro: `hold` estica).
const visualSecs = (s: Scene) => {
  const clip = CLIPS[s.id];
  if (!clip) return 0;
  const avail = s.cuts ? s.cuts.reduce((a, [x, y]) => a + y - x, 0) : clip.length - 0.3;
  return Math.min(avail, TTS[s.id] + VOICE_DELAY + (s.hold ?? 2));
};
// A cena dura a fala; se a gravação for mais longa, ela acelera até MAX_RATE e o resto estica a cena.
const sceneSecs = (s: Scene) => Math.max(DUR[s.id] + VOICE_DELAY + TAIL, visualSecs(s) / MAX_RATE);
const sceneFrames = (s: Scene) => Math.round(sceneSecs(s) * FPS);
const rateOf = (s: Scene) => {
  const v = visualSecs(s);
  return v ? Math.min(MAX_RATE, Math.max(1, v / sceneSecs(s))) : 1;
};

export function professorFrames() {
  return SCENES.reduce((a, s) => a + sceneFrames(s), 0) - TRANSITION * (SCENES.length - 1);
}

/** Divide a narração em frases curtas e distribui o tempo pelo tamanho de cada uma. */
function captionAt(text: string, total: number, t: number): string {
  const sentences = text.match(/[^.!?:]+[.!?:]?/g)?.map((s) => s.trim()).filter(Boolean) ?? [text];
  const chunks: string[] = [];
  for (const s of sentences) {
    if (s.length <= 95) { chunks.push(s); continue; }
    let cur = "";
    for (const part of s.split(/(?<=,)\s+/)) {
      if ((cur + " " + part).trim().length > 95 && cur) { chunks.push(cur.trim()); cur = part; }
      else cur = (cur + " " + part).trim();
    }
    if (cur) chunks.push(cur);
  }
  const weights = chunks.map((c) => c.length + 12);
  const sum = weights.reduce((a, b) => a + b, 0);
  let acc = 0;
  for (let i = 0; i < chunks.length; i++) {
    acc += (weights[i] / sum) * total;
    if (t < acc) return chunks[i];
  }
  return t < total + 0.4 ? chunks[chunks.length - 1] : "";
}

function Narrated({ scene, captionLeft = 0 }: { scene: Scene; captionLeft?: number }) {
  const f = useCurrentFrame();
  const t = f / FPS - VOICE_DELAY;
  return (
    <>
      <Sequence from={Math.round(VOICE_DELAY * FPS)}>
        <Audio src={staticFile(`professor/voice/${scene.id}.wav`)} />
      </Sequence>
      {t >= 0 && <Caption text={captionAt(scene.text, DUR[scene.id], t)} left={captionLeft} />}
    </>
  );
}

function ScreenScene({ scene, showChapter }: { scene: Scene; showChapter: boolean }) {
  const f = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const clip = CLIPS[scene.id];
  const zoom = interpolate(f, [0, durationInFrames], [1, 1.035]);
  const mobile = scene.id === "celular";
  const rate = rateOf(scene);
  return (
    <AbsoluteFill>
      <Backdrop />
      {clip && !mobile && (
        <AbsoluteFill style={{ transform: `scale(${zoom})` }}>
          {scene.cuts ? (
            // Trechos escolhidos da gravação (pula a digitação do login entre um papel e outro).
            <Series>
              {scene.cuts.map(([a, b]) => (
                <Series.Sequence key={a} durationInFrames={Math.round(((b - a) * FPS) / rate)}>
                  <OffthreadVideo src={staticFile(`professor/${clip.file}`)} startFrom={Math.round(a * FPS)} playbackRate={rate} muted
                    style={{ width: 1920, height: 1080, objectFit: "cover" }} />
                </Series.Sequence>
              ))}
            </Series>
          ) : (
            <OffthreadVideo src={staticFile(`professor/${clip.file}`)} startFrom={Math.round(clip.offset * FPS)} playbackRate={rate} muted
              style={{ width: 1920, height: 1080, objectFit: "cover" }} />
          )}
        </AbsoluteFill>
      )}
      {clip && mobile && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 120 }}>
          <PhoneFrame height={900}>
            <OffthreadVideo src={staticFile(`professor/${clip.file}`)} startFrom={Math.round(clip.offset * FPS)} playbackRate={rate} muted
              // A gravação com escala 2 deixa a página no quarto de cima à esquerda do quadro: amplia 2x a partir do canto.
              style={{ position: "absolute", left: 0, top: 0, width: "200%", height: "200%", objectFit: "fill" }} />
          </PhoneFrame>
          <div style={{ width: 560, color: C.chrome, fontFamily: FONT_DISPLAY, fontSize: 64, fontWeight: 800, lineHeight: 1.05, letterSpacing: "-0.03em" }}>
            O painel no bolso.
            <div style={{ marginTop: 22, fontFamily: FONT_BODY, fontSize: 30, fontWeight: 400, color: C.steel, lineHeight: 1.4 }}>
              Instala como aplicativo, sem loja. Atalhos do dia a dia na barra de baixo.
            </div>
          </div>
        </AbsoluteFill>
      )}
      {showChapter && <ChapterTag label={scene.chapter} />}
      <Narrated scene={scene} captionLeft={mobile ? 900 : 0} />
    </AbsoluteFill>
  );
}

function TitleCard({ scene }: { scene: Scene }) {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({ frame: f, fps, config: { damping: 16 } });
  const s2 = spring({ frame: f - 10, fps, config: { damping: 18 } });
  const outro = scene.id === "outro";
  return (
    <AbsoluteFill>
      <Backdrop />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 34 }}>
        <div style={{ transform: `scale(${0.7 + s * 0.3})`, opacity: s }}>
          <Logo size={outro ? 120 : 150} />
        </div>
        <div style={{ opacity: s2, transform: `translateY(${(1 - s2) * 30}px)`, textAlign: "center" }}>
          <div style={{ fontFamily: FONT_DISPLAY, fontWeight: 800, fontSize: 76, letterSpacing: "-0.03em", color: C.chrome }}>
            {outro ? "Obrigado." : (
              <>O sistema <span style={{ background: C.grad, WebkitBackgroundClip: "text", color: "transparent" }}>por dentro</span></>
            )}
          </div>
          <div style={{ marginTop: 16, fontFamily: FONT_MONO, fontSize: 24, letterSpacing: "0.16em", color: C.steel }}>
            {outro ? "AGENDA · COMANDAS · CAIXA · FIDELIZAÇÃO · SITE · EQUIPE" : "GESTÃO PARA BARBEARIAS · DEMONSTRAÇÃO COM DADOS FICTÍCIOS"}
          </div>
        </div>
      </AbsoluteFill>
      <Narrated scene={scene} />
    </AbsoluteFill>
  );
}

// Trechos (em quadros do vídeo todo) em que alguém está falando: a música abaixa neles.
const VOICE_WINDOWS = (() => {
  const w: [number, number][] = [];
  let start = 0;
  for (const s of SCENES) {
    const a = start + Math.round(VOICE_DELAY * FPS);
    w.push([a, a + Math.round(DUR[s.id] * FPS)]);
    start += sceneFrames(s) - TRANSITION;
  }
  return w;
})();

function musicVolume(f: number, total: number) {
  const RAMP = 15;
  let near = Infinity;
  for (const [a, b] of VOICE_WINDOWS) near = Math.min(near, f < a ? a - f : f > b ? f - b : 0);
  const duck = interpolate(near, [0, RAMP], [0.07, 0.17], { extrapolateRight: "clamp" });
  const edges = Math.min(interpolate(f, [0, 30], [0, 1], { extrapolateRight: "clamp" }),
    interpolate(f, [total - 75, total], [1, 0], { extrapolateLeft: "clamp" }));
  return duck * edges;
}

export function Professor() {
  const { durationInFrames } = useVideoConfig();
  let prevChapter = "";
  return (
    <AbsoluteFill style={{ background: C.obsidian }}>
      {/* Jimmy Blythe, "Armour Avenue Struggle" (1924): domínio público. */}
      {MUSIC && <Audio src={staticFile("music/blues-bed.wav")} volume={(f) => musicVolume(f, durationInFrames)} />}
      <TransitionSeries>
        {SCENES.flatMap((s, i) => {
          const showChapter = !!s.chapter && s.chapter !== prevChapter;
          prevChapter = s.chapter || prevChapter;
          const items = [
            <TransitionSeries.Sequence key={s.id} durationInFrames={sceneFrames(s)}>
              {s.card ? <TitleCard scene={s} /> : <ScreenScene scene={s} showChapter={showChapter} />}
            </TransitionSeries.Sequence>,
          ];
          if (i < SCENES.length - 1)
            items.push(<TransitionSeries.Transition key={s.id + "-t"} presentation={fade()} timing={linearTiming({ durationInFrames: TRANSITION })} />);
          return items;
        })}
      </TransitionSeries>
    </AbsoluteFill>
  );
}
