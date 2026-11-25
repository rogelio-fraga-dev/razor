// Entra no painel demo e fotografa o painel + o site público (conferência visual).
import { chromium } from "playwright";
const out = process.argv[2];
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
p.on("pageerror", (e) => errors.push(e.message));
await p.goto("http://localhost:3000/login");
await p.fill('input[type="email"]', "dono@navalhademo.local");
await p.fill('input[type="password"]', "demo12345");
await p.click('button[type="submit"]');
await p.waitForTimeout(2500);
await p.keyboard.press("Escape");
await p.screenshot({ path: `${out}/p0-dashboard-early.png` });
await p.waitForTimeout(1500);
await p.screenshot({ path: `${out}/p1-dashboard.png` });
await p.click('a[href="/agenda"]').catch(() => {});
await p.waitForTimeout(120);
await p.screenshot({ path: `${out}/p2-transition.png` });
await p.waitForTimeout(1500);
await p.screenshot({ path: `${out}/p3-agenda.png` });
await p.evaluate(() => window.dispatchEvent(new CustomEvent("razor:stamp", { detail: { text: "PAGO", tone: "ok" } })));
await p.waitForTimeout(450);
await p.screenshot({ path: `${out}/p4-stamp.png` });
const s = await b.newPage({ viewport: { width: 1440, height: 900 } });
s.on("pageerror", (e) => errors.push(e.message));
await s.goto("http://localhost:3000/b/demo-navalha", { waitUntil: "networkidle" });
const y = await s.evaluate(() => { const el = document.querySelector("#casa"); return el ? el.getBoundingClientRect().top + scrollY : 0; });
for (const [i, off] of [[0, -500], [1, -250], [2, 0]]) {
  await s.evaluate((v) => scrollTo(0, v), y + off);
  await s.waitForTimeout(900);
  await s.screenshot({ path: `${out}/s${i}-site.png` });
}
console.log(JSON.stringify(errors));
await b.close();
