import { chromium } from "playwright";
import fs from "node:fs";
const out = process.argv[2];
const env = fs.readFileSync("C:/projetos/razor/.env", "utf8");
const v = (k) => (env.match(new RegExp(`^${k}=(.*)$`, "m")) || [])[1]?.trim().replace(/^["']|["']$/g, "");
const b = await chromium.launch({ args: ["--lang=pt-BR"] }); const p = await b.newPage({ viewport: { width: 1600, height: 900 } });
await p.goto("http://localhost:3000/plataforma"); await p.waitForTimeout(1200);
await p.fill('input[type="email"]', v("RAZOR_PLATFORM_ADMIN_EMAIL") || "admin@razor.local");
await p.fill('input[type="password"]', v("RAZOR_PLATFORM_ADMIN_PASSWORD") || "");
await p.getByRole("button", { name: "Entrar" }).click(); await p.waitForTimeout(2500);
await p.screenshot({ path: `${out}/pl-0.png` });
let i = 1;
for (const t of ["Segurança"]) {
  const ok = await p.getByRole("tab", { name: new RegExp(t) }).or(p.getByRole("button", { name: new RegExp("^" + t) })).first().click({ timeout: 2000 }).then(() => true).catch(() => false);
  await p.waitForTimeout(1500); await p.screenshot({ path: `${out}/pl-${i++}.png` }); console.log(t, ok);
}
await b.close();
