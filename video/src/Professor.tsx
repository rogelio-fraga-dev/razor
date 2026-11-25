// Vídeo de demonstração para o professor: o sistema por dentro (sem a conversa do bot).
// Cenas, narração e clipes vêm de professor/script.json, public/professor/durations.json
// (gerado por tools/tts.py) e public/professor/clips.json (gerado por professor/record.mjs).
import { AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { loadFont as loadArchivo } from "@remotion/google-fonts/Archivo";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import script from "../professor/script.json";
import durations from "../public/professor/durations.json";
import clips from "../public/professor/clips.json";
import { Backdrop, C, Caption, ChapterTag, FONT_BODY, FONT_DISPLAY, FONT_MONO, Logo, PhoneFrame } from "./brand";

loadArchivo("normal", { weights: ["700", "800"], subsets: ["latin"] });
loadInter("normal", { weights: ["400", "600"], subsets: ["latin"] });
loadMono("normal", { weights: ["500", "700"], subsets: ["latin"] });

export const FPS = 30;
const TRANSITION = 12;
const VOICE_DELAY = 0.35; // s antes da narração começar em cada cena
const TAIL = 1.1; // s de respiro depois da narração

type Scene = { id: string; chapter: string; text: string; card?: boolean; hold?: number };
const SCENES = script.scenes as Scene[];
const DUR = durations as Record<string, number>;
const CLIPS = clips as Record<string, { file: string; offset: number; length: number }>;

// A cena dura a narração; se a gravação mostra algo depois (o carimbo PAGO, o tema claro), `hold` estica.
const sceneFrames = (s: Scene) => {
  const narr = DUR[s.id] + VOICE_DELAY + TAIL;
  const clip = CLIPS[s.id];
  const visual = clip ? Math.min(clip.length - 0.3, DUR[s.id] + VOICE_DELAY + (s.hold ?? 2)) : 0;
  return Math.round(Math.max(narr, visual) * FPS);
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

function Narrated({ scene }: { scene: Scene }) {
  const f = useCurrentFrame();
  const t = f / FPS - VOICE_DELAY;
  return (
    <>
      <Sequence from={Math.round(VOICE_DELAY * FPS)}>
        <Audio src={staticFile(`professor/${scene.id}.wav`)} />
      </Sequence>
      {t >= 0 && <Caption text={captionAt(scene.text, DUR[scene.id], t)} />}
    </>
  );
}

function ScreenScene({ scene, showChapter }: { scene: Scene; showChapter: boolean }) {
  const f = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const clip = CLIPS[scene.id];
  const zoom = interpolate(f, [0, durationInFrames], [1, 1.035]);
  const mobile = scene.id === "celular";
  return (
    <AbsoluteFill>
      <Backdrop />
      {clip && !mobile && (
        <AbsoluteFill style={{ transform: `scale(${zoom})` }}>
          <OffthreadVideo src={staticFile(`professor/${clip.file}`)} startFrom={Math.round(clip.offset * FPS)} muted
            style={{ width: 1920, height: 1080, objectFit: "cover" }} />
        </AbsoluteFill>
      )}
      {clip && mobile && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 120 }}>
          <PhoneFrame height={900}>
            <OffthreadVideo src={staticFile(`professor/${clip.file}`)} startFrom={Math.round(clip.offset * FPS)} muted
              style={{ width: "100%", height: "100%", objectFit: "cover" }} />
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
      <Narrated scene={scene} />
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

export function Professor() {
  let prevChapter = "";
  return (
    <AbsoluteFill style={{ background: C.obsidian }}>
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
