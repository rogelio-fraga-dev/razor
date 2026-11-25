// Fotografa a landing em pontos da rolagem (conferência visual do upgrade).
// Uso: node tools/shoot-landing.mjs <url> <pasta-saida> [largura] [altura]
import { chromium } from "playwright";
const [url = "http://localhost:8765/", out = "shots", w = "1440", h = "900"] = process.argv.slice(2);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: +w, height: +h } });
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
await page.goto(url, { waitUntil: "networkidle" });
await page.waitForTimeout(2200);
await page.screenshot({ path: `${out}/00-hero.png` });
const track = await page.$("#day-track");
const box = await page.evaluate(() => {
  const r = document.querySelector("#day-track").getBoundingClientRect();
  return { top: r.top + scrollY, height: r.height };
});
for (let i = 0; i < 4; i++) {
  const y = box.top + (box.height - +h) * (i / 4 + 0.12);
  await page.evaluate((y) => scrollTo(0, y), y);
  await page.waitForTimeout(1800);
  await page.screenshot({ path: `${out}/1${i}-dia.png` });
}
await page.evaluate(() => document.querySelector("#calculadora").scrollIntoView());
await page.waitForTimeout(800);
await page.fill("#c-cortes", "900").catch(() => {});
await page.evaluate(() => { const el = document.querySelector("#c-cortes"); el.value = 900; el.dispatchEvent(new Event("input")); });
await page.waitForTimeout(1000);
await page.screenshot({ path: `${out}/20-calc.png` });
console.log(JSON.stringify({ errors, track: !!track }));
await browser.close();
