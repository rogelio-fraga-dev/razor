// Biblioteca do gravador: navegador com cursor visível, movimentos suaves e simulação do
// estado "WhatsApp conectado" (só na gravação — nada é ligado de verdade).
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

export const BASE = "http://localhost:3000";
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Cursor desenhado na página (o modo sem janela não grava o ponteiro) + onda no clique.
export const CURSOR = `
(() => {
  const mk = () => {
    if (document.getElementById("__cur")) return;
    const s = document.createElement("style");
    s.textContent = \`#__cur{position:fixed;left:0;top:0;width:26px;height:26px;z-index:2147483647;pointer-events:none;
      transform:translate(-100px,-100px);transition:transform .03s linear}
      #__cur svg{filter:drop-shadow(0 2px 4px rgba(0,0,0,.5))}
      .__rip{position:fixed;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;z-index:2147483646;pointer-events:none;
      border:3px solid rgba(55,226,255,.9);animation:__r .55s ease-out forwards}
      @keyframes __r{from{transform:scale(.3);opacity:1}to{transform:scale(1.4);opacity:0}}
      ::-webkit-scrollbar{width:0;height:0}\`;
    document.documentElement.appendChild(s);
    const c = document.createElement("div"); c.id = "__cur";
    c.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M4 2l16 9-7 2-3 7z" fill="#fff" stroke="#111" stroke-width="1.4" stroke-linejoin="round"/></svg>';
    document.documentElement.appendChild(c);
    addEventListener("mousemove", (e) => { c.style.transform = \`translate(\${e.clientX - 3}px,\${e.clientY - 2}px)\`; }, true);
    addEventListener("mousedown", (e) => { const r = document.createElement("div"); r.className = "__rip";
      r.style.left = e.clientX + "px"; r.style.top = e.clientY + "px"; document.documentElement.appendChild(r);
      setTimeout(() => r.remove(), 700); }, true);
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mk); else mk();
})();`;

/** Simula WhatsApp conectado e sistema saudável (o serviço de WhatsApp fica desligado de propósito). */
export async function mockHealthy(ctx) {
  const json = (route, body) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  await ctx.route("**/api/admin/settings/bot/status", (r) =>
    json(r, { instanceName: "DemoNavalhaWpp", whatsappState: "open", whatsappConnected: true, aiConfigured: true, botEnabled: true }));
  await ctx.route("**/api/admin/whatsapp/status", (r) => json(r, { instanceName: "DemoNavalhaWpp", state: "open", connected: true }));
  await ctx.route("**/api/admin/system-health/alerts", (r) => json(r, []));
  await ctx.route("**/api/admin/system-health", async (r) => {
    const res = await r.fetch();
    const body = await res.json().catch(() => ({}));
    json(r, { ...body, open: [] });
  });
}

/**
 * Grava uma cena. `fn(page, h)` faz as ações; o clipe dura pelo menos `minSeconds`.
 * Devolve { file, offset } — offset = segundos do início do vídeo até o começo da cena.
 */
export async function record(outDir, id, minSeconds, fn, opts = {}) {
  const { width = 1600, height = 900, storageState, scale = 1, mobile = false, dsf = 1 } = opts;
  const browser = await chromium.launch({ args: ["--lang=pt-BR"] });
  const dir = path.join(outDir, "_tmp_" + id);
  fs.rmSync(dir, { recursive: true, force: true });
  const ctx = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: dsf,
    isMobile: mobile,
    hasTouch: mobile,
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    colorScheme: "dark",
    storageState,
    recordVideo: { dir, size: { width: width * scale, height: height * scale } },
  });
  await ctx.addInitScript(CURSOR);
  await mockHealthy(ctx);
  const t0 = Date.now();
  const page = await ctx.newPage();
  const h = helpers(page, ctx);
  let start = 0;
  h.markStart = () => { start = (Date.now() - t0) / 1000; };
  try {
    await fn(page, h);
  } catch (e) {
    console.log(`  [${id}] ação falhou: ${e.message.split("\n")[0]}`);
  }
  if (!start) start = 0;
  const elapsed = (Date.now() - t0) / 1000 - start;
  if (elapsed < minSeconds) await sleep((minSeconds - elapsed) * 1000);
  const video = page.video();
  await ctx.close();
  const src = await video.path();
  const file = path.join(outDir, `${id}.webm`);
  fs.copyFileSync(src, file);
  fs.rmSync(dir, { recursive: true, force: true });
  await browser.close();
  const total = (Date.now() - t0) / 1000;
  console.log(`  [${id}] ok · início ${start.toFixed(1)}s · duração ${(total - start).toFixed(1)}s`);
  return { file: `${id}.webm`, offset: +start.toFixed(2), length: +(total - start).toFixed(2) };
}

export function helpers(page, ctx) {
  let mx = 800, my = 450;
  const h = {
    async go(route, wait = 1400) {
      await page.goto(BASE + route, { waitUntil: "domcontentloaded" });
      await sleep(wait);
      await page.getByRole("button", { name: /Pular tour/i }).click({ timeout: 400 }).catch(() => {});
    },
    async move(x, y, ms = 700) {
      const steps = Math.max(8, Math.round(ms / 16));
      const sx = mx, sy = my;
      for (let i = 1; i <= steps; i++) {
        const t = i / steps;
        const e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        await page.mouse.move(sx + (x - sx) * e, sy + (y - sy) * e);
        await sleep(ms / steps);
      }
      mx = x; my = y;
    },
    async to(loc, ms = 700) {
      const l = typeof loc === "string" ? page.locator(loc).first() : loc.first();
      await l.scrollIntoViewIfNeeded({ timeout: 2500 }).catch(() => {});
      const b = await l.boundingBox({ timeout: 2500 }).catch(() => null);
      if (!b) return false;
      await h.move(b.x + Math.min(b.width / 2, 60), b.y + b.height / 2, ms);
      return true;
    },
    async click(loc, after = 900) {
      const l = typeof loc === "string" ? page.locator(loc).first() : loc.first();
      if (!(await h.to(l))) { console.log("    (não achei para clicar)"); return false; }
      await sleep(180);
      await page.mouse.down(); await sleep(70); await page.mouse.up();
      await sleep(after);
      return true;
    },
    text: (t, exact = false) => page.getByText(t, { exact }),
    role: (role, name) => page.getByRole(role, { name }),
    async wheel(total, step = 120, pause = 45) {
      const n = Math.abs(Math.round(total / step));
      for (let i = 0; i < n; i++) { await page.mouse.wheel(0, Math.sign(total) * step); await sleep(pause); }
      await sleep(500);
    },
    async type(sel, value, delay = 55) {
      await h.click(sel, 250);
      await page.locator(sel).first().pressSequentially(value, { delay });
      await sleep(300);
    },
    sleep,
    page,
    ctx,
  };
  return h;
}

/** Faz login uma vez e salva os cookies (para as cenas já começarem dentro do painel). */
export async function loginState(email, password, file) {
  const b = await chromium.launch();
  const ctx = await b.newContext();
  const p = await ctx.newPage();
  await p.goto(BASE + "/login");
  await p.fill('input[type="email"]', email);
  await p.fill('input[type="password"]', password);
  await p.click('button[type="submit"]');
  await p.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 15000 });
  await sleep(1500);
  await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 800 }).catch(() => {});
  await ctx.storageState({ path: file });
  await b.close();
  return file;
}
