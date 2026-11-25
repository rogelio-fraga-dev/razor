// Gravação nítida para tela de celular: captura os quadros do Chrome (CDP screencast) na
// densidade real do aparelho (3x) e monta o MP4 com o ffmpeg. O recordVideo do Playwright
// grava em pixels CSS (390 px de largura), o que fica borrado num vídeo vertical 1080p.
import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { CURSOR, mockHealthy, helpers, sleep } from "./rec-lib.mjs";

const FF = process.env.FFMPEG ||
  "C:/Users/rogelio/AppData/Local/Programs/Python/Python312/Lib/site-packages/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v9.0.2.exe";
// No celular não há seta de mouse: só a onda do toque.
const HIDE_ARROW = `addEventListener("DOMContentLoaded",()=>{const s=document.createElement("style");s.textContent="#__cur{display:none!important}";document.documentElement.appendChild(s)});`;

export async function recordSharp(outFile, fn, { width = 390, height = 844, dsf = 3, storageState } = {}) {
  const browser = await chromium.launch({ args: ["--lang=pt-BR"] });
  const ctx = await browser.newContext({
    viewport: { width, height }, deviceScaleFactor: dsf, isMobile: true, hasTouch: true,
    locale: "pt-BR", timezoneId: "America/Sao_Paulo", colorScheme: "dark", storageState,
  });
  await ctx.addInitScript(CURSOR);
  await ctx.addInitScript(HIDE_ARROW);
  await mockHealthy(ctx);
  const page = await ctx.newPage();
  const cdp = await ctx.newCDPSession(page);
  const frames = [];
  let on = false;
  cdp.on("Page.screencastFrame", async ({ data, metadata, sessionId }) => {
    if (on) frames.push({ t: metadata.timestamp, data });
    await cdp.send("Page.screencastFrameAck", { sessionId }).catch(() => {});
  });
  const h = helpers(page, ctx);
  h.start = async () => {
    await cdp.send("Page.startScreencast", { format: "jpeg", quality: 92, maxWidth: width * dsf, maxHeight: height * dsf, everyNthFrame: 1 });
    on = true;
  };
  try { await fn(page, h); } catch (e) { console.log(`  ação falhou: ${e.message.split("\n")[0]}`); }
  await sleep(400);
  on = false;
  const stopT = Date.now() / 1000;
  await cdp.send("Page.stopScreencast").catch(() => {});
  await browser.close();
  if (!frames.length) throw new Error("nenhum quadro capturado");

  const dir = outFile + "_frames";
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  const lines = [];
  frames.forEach((f, i) => {
    const name = `f${String(i).padStart(5, "0")}.jpg`;
    fs.writeFileSync(path.join(dir, name), Buffer.from(f.data, "base64"));
    const dur = i < frames.length - 1 ? frames[i + 1].t - f.t : Math.max(0.1, stopT - f.t);
    lines.push(`file '${name}'`, `duration ${Math.max(0.001, dur).toFixed(4)}`);
  });
  lines.push(`file 'f${String(frames.length - 1).padStart(5, "0")}.jpg'`);
  fs.writeFileSync(path.join(dir, "list.txt"), lines.join("\n"));
  execFileSync(FF, ["-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", path.join(dir, "list.txt"),
    "-vf", `fps=30,scale=${width * dsf}:${height * dsf}:flags=lanczos,format=yuv420p`, "-c:v", "libx264", "-crf", "16", outFile]);
  fs.rmSync(dir, { recursive: true, force: true });
  const secs = stopT - frames[0].t;
  console.log(`  ${path.basename(outFile)}: ${frames.length} quadros, ${secs.toFixed(1)} s`);
  return secs;
}
