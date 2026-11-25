// Passa por todas as telas do painel demo e fotografa cada uma (conferência dos dados).
import { chromium } from "playwright";
const out = process.argv[2];
const routes = ["/", "/agenda", "/comandas", "/customers", "/retention", "/plans", "/packages", "/coupons",
  "/caixa", "/commissions", "/commissions/settlement", "/expenses", "/products", "/services", "/professionals", "/settings", "/profile"];
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1536, height: 864 }, locale: "pt-BR", timezoneId: "America/Sao_Paulo" });
const p = await ctx.newPage();
const errors = [];
p.on("pageerror", (e) => errors.push(e.message));
await p.goto("http://localhost:3000/login");
await p.fill('input[type="email"]', "dono@navalhademo.local");
await p.fill('input[type="password"]', "demo12345");
await p.click('button[type="submit"]');
await p.waitForTimeout(2500);
await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 600 }).catch(() => {});
for (const r of routes) {
  await p.goto("http://localhost:3000" + r, { timeout: 15000 }).catch((e) => console.log("goto fail " + r));
  await p.waitForTimeout(1800);
  await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 600 }).catch(() => {});
  await p.screenshot({ path: `${out}/${r === "/" ? "home" : r.slice(1).replace(/\//g, "-")}.png` });
  const text = (await p.locator("main, .content").first().innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 400);
  console.log(`## ${r}: ${text}`);
}
console.log(JSON.stringify(errors));
await b.close();
