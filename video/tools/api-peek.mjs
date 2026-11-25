import { chromium } from "playwright";
const b = await chromium.launch(); const p = await b.newPage();
await p.goto("http://localhost:3000/login");
await p.fill('input[type="email"]', "dono@navalhademo.local"); await p.fill('input[type="password"]', "demo12345");
await p.click('button[type="submit"]'); await p.waitForTimeout(2000);
for (const u of process.argv.slice(2)) {
  const x = await p.request.get("http://localhost:3000" + u); const r = x.status() + " " + (await x.text()).slice(0, 700);
  console.log("== " + u + "\n" + r);
}
await b.close();
