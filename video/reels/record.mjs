// Grava as telas do painel para o Reels (celular, nítido). Uso: node reels/record.mjs [cena,...]
import path from "node:path";
import fs from "node:fs";
import { loginState, sleep } from "../tools/rec-lib.mjs";
import { recordSharp } from "../tools/rec-sharp.mjs";

const OUT = path.resolve("public/reels");
const STATE = path.resolve("out/.state");
fs.mkdirSync(STATE, { recursive: true });
const owner = await loginState("dono@navalhademo.local", "demo12345", path.join(STATE, "owner.json"));
const only = process.argv[2]?.split(",");

const tap = async (p, h, loc, after = 700) => {
  const l = loc.first();
  await l.scrollIntoViewIfNeeded({ timeout: 2500 }).catch(() => {});
  const b = await l.boundingBox({ timeout: 2500 }).catch(() => null);
  if (!b) { console.log("    (não achei para tocar)"); return false; }
  await p.mouse.move(b.x + b.width / 2, b.y + b.height / 2);
  await p.mouse.down(); await sleep(60); await p.mouse.up();
  await sleep(after);
  return true;
};
const swipe = async (p, dy, steps = 14) => { for (let i = 0; i < steps; i++) { await p.mouse.wheel(0, dy / steps); await sleep(28); } await sleep(300); };

const SCENES = {
  async painel(p, h) {
    await h.go("/", 1600);
    await p.getByText(/O que a IA fez por você/i).first().evaluate((e) => e.closest("section,div").scrollIntoView({ block: "start" })).catch(() => {});
    await p.evaluate(() => scrollBy(0, -12));
    await sleep(500);
    await h.start();
    await sleep(3400);
  },
  async agenda(p, h) {
    await h.go("/agenda", 1400);
    await h.start();
    await sleep(900);
    await tap(p, h, p.getByRole("button", { name: /Caio Máquina/ }), 900);
    await tap(p, h, p.getByRole("button", { name: /Rafael Tesoura/ }), 1000);
    await swipe(p, 200);
    await sleep(800);
  },
  async comanda(p, h) {
    await h.go("/comandas", 1400);
    await h.start();
    await sleep(700);
    await tap(p, h, p.locator("button.btn-primary", { hasText: "Fechar R$" }), 1200);
    const sel = p.getByLabel("Forma de pagamento da parte 1");
    await tap(p, h, sel, 300);
    await sel.selectOption({ label: "PIX" }).catch(() => sel.selectOption({ index: 1 }));
    await sleep(700);
    // O confirmar é o botão ao lado do "Voltar" no rodapé da janela.
    await tap(p, h, p.getByRole("button", { name: "Voltar" }).locator("xpath=following-sibling::button[1]"), 2800);
  },
  async caixa(p, h) {
    await h.go("/caixa", 1400);
    await h.start();
    await sleep(1600);
    await swipe(p, 240);
    await sleep(1200);
  },
  async retencao(p, h) {
    await h.go("/retention", 1500);
    await h.start();
    await sleep(1200);
    await swipe(p, 320);
    await sleep(1400);
  },
};

const lens = {};
for (const [id, fn] of Object.entries(SCENES)) {
  if (only && !only.includes(id)) continue;
  console.log("gravando " + id);
  lens[id] = await recordSharp(path.join(OUT, id + ".mp4"), fn, { storageState: owner });
}
const f = path.join(OUT, "clips.json");
const old = fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : {};
fs.writeFileSync(f, JSON.stringify({ ...old, ...lens }, null, 1));
