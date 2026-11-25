// Lista botões/links e elementos clicáveis de uma tela (para escrever o roteiro de gravação).
import { chromium } from "playwright";
const [route, clickText] = process.argv.slice(2);
const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 1600, height: 900 }, locale: "pt-BR" }); const p = await ctx.newPage();
await p.goto("http://localhost:3000/login");
await p.fill('input[type="email"]', "dono@navalhademo.local"); await p.fill('input[type="password"]', "demo12345");
await p.click('button[type="submit"]'); await p.waitForTimeout(2000);
await p.goto("http://localhost:3000" + route); await p.waitForTimeout(2000);
await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 500 }).catch(() => {});
if (clickText) { await p.getByText(clickText, { exact: false }).first().click({ timeout: 3000 }).catch((e) => console.log("click fail")); await p.waitForTimeout(1500); }
console.log(await p.evaluate(() => {
  const out = [];
  document.querySelectorAll("button, a, [role=button], [role=tab], tr[tabindex], [class*=appt], [class*=row]").forEach((e) => {
    const r = e.getBoundingClientRect(); if (r.width === 0) return;
    out.push(`${e.tagName.toLowerCase()}.${(e.className && e.className.toString().split(" ").slice(0, 2).join(".")) || ""} "${(e.getAttribute("aria-label") || e.textContent || "").trim().replace(/\s+/g, " ").slice(0, 40)}"`);
  });
  return [...new Set(out)].slice(0, 90).join("\n");
}));
await b.close();
