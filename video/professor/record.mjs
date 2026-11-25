// Grava as cenas do vídeo do professor no painel local (dados demo fictícios).
// Uso: node professor/record.mjs [cena1,cena2,...]   (sem argumento: todas)
// Saída: public/professor/<cena>.webm + clips.json (offset e duração de cada clipe).
import fs from "node:fs";
import path from "node:path";
import { record, loginState, sleep } from "../tools/rec-lib.mjs";

const OUT = path.resolve("public/professor");
const durations = JSON.parse(fs.readFileSync(path.join(OUT, "durations.json"), "utf8"));
const only = process.argv[2]?.split(",");
const clipsFile = path.join(OUT, "clips.json");
const clips = fs.existsSync(clipsFile) ? JSON.parse(fs.readFileSync(clipsFile, "utf8")) : {};
const STATE = path.resolve("out/.state");
fs.mkdirSync(STATE, { recursive: true });

// Senha do console da plataforma: lida do .env local, nunca impressa.
const env = fs.readFileSync("C:/projetos/razor/.env", "utf8");
const envVar = (k) => (env.match(new RegExp(`^${k}=(.*)$`, "m")) || [])[1]?.trim().replace(/^["']|["']$/g, "");

const owner = await loginState("dono@navalhademo.local", "demo12345", path.join(STATE, "owner.json"));
const ownerOpts = { storageState: owner };
const min = (id) => (durations[id] ?? 10) + 1.4;

const SCENES = {
  async cadastro(p, h) {
    await h.go("/cadastro", 1200);
    h.markStart();
    await h.type("#su-shop", "Barbearia Navalha de Prata", 28);
    await h.type("#su-owner", "Ricardo Prata", 28);
    await h.type("#su-city", "São Paulo", 28);
    await h.type("#su-phone", "11999990001", 28);
    await h.type("#su-email", "contato@navalhadeprata.com.br", 18);
    await h.type("#su-pass", "senha-forte-123", 18);
    await sleep(600);
    // Primeiro acesso: o assistente de configuração (barbearia demo já logada).
    const st = JSON.parse(fs.readFileSync(owner, "utf8"));
    await h.ctx.addCookies(st.cookies);
    await h.go("/comecar", 1600);
    await h.move(700, 400, 900);
    await sleep(1500);
    await h.wheel(400);
    await sleep(2500);
    await h.wheel(-400);
    await sleep(2500);
  },

  async painel(p, h) {
    await h.go("/", 600);
    h.markStart();
    await sleep(1500);
    await h.to(".cockpit, [class*=cockpit]", 900);
    await sleep(1800);
    await h.to(h.text(/O QUE A IA FEZ/i), 900);
    await sleep(1200);
    await h.wheel(560, 80, 60);
    await sleep(1800);
    await h.to(h.text(/mensagens respondidas/i), 700);
    await sleep(1200);
    await h.to(h.text(/faltas evitadas/i), 700);
    await sleep(1500);
    await h.wheel(420, 80, 60);
    await sleep(1500);
    await h.to(h.text(/Faturamento do mês/i), 700);
    await sleep(1200);
    await h.to(h.text(/Tíquete médio/i), 700);
    await sleep(1000);
    await h.wheel(360, 80, 60);
    await h.click(h.role("button", /^30D$/), 1500);
    await h.click(h.role("button", /^MÊS$/i), 1500);
    await h.wheel(400, 80, 60);
    await sleep(1500);
  },

  async busca(p, h) {
    await h.go("/", 900);
    h.markStart();
    await h.to(".kbd-hint", 600);
    await sleep(300);
    await p.keyboard.press("Control+k");
    await sleep(600);
    await p.keyboard.type("cai", { delay: 180 });
    await sleep(1300);
    await p.keyboard.press("Escape");
    await sleep(400);
    await h.click('button[aria-label="Alternar tema"]', 1700);
    await h.move(900, 500, 600);
    await sleep(600);
    await h.click('button[aria-label="Alternar tema"]', 900);
  },

  async agenda(p, h) {
    await h.go("/agenda", 900);
    h.markStart();
    await h.move(1000, 160, 900);
    await sleep(1200);
    await h.to(".cal-event.ev-pix", 800);
    await sleep(900);
    await h.to(".cal-event.ev-presencial", 700);
    await sleep(900);
    await h.to(".cal-event.ev-pendente", 700);
    await sleep(900);
    await h.click(h.role("button", /^Semana$/), 2400);
    await h.click(h.role("button", /^Dia$/), 1200);
    await h.click(".cal-event.ev-presencial", 2600);
    await p.keyboard.press("Escape");
    await sleep(700);
    await h.click(h.role("button", /\+ Agendar/), 2600);
    await p.keyboard.press("Escape");
    await sleep(600);
    await h.click(h.role("button", /Folgas/), 2400);
    await p.keyboard.press("Escape");
    await sleep(600);
  },

  async comandas(p, h) {
    await h.go("/comandas", 900);
    h.markStart();
    await h.move(800, 200, 700);
    await sleep(600);
    const sel = p.locator("select.input.grow").first();
    await h.to(sel, 700);
    await sel.selectOption({ index: 8 }).catch(() => {});
    await sleep(900);
    await h.click(p.getByRole("button", { name: "Adicionar", exact: true }), 1400);
    await h.click(p.locator("button.btn-primary", { hasText: "Fechar R$" }), 1800);
    await h.click(h.role("button", /Dividir em outra forma/), 1400);
    const parts = p.locator('input[aria-label^="Valor da parte"]');
    const total = (await p.locator(".modal, [role=dialog]").first().innerText().catch(() => "")).match(/Total a cobrar\s*R\$\s*([\d.,]+)/);
    if (total && (await parts.count()) >= 2) {
      const v = parseFloat(total[1].replace(".", "").replace(",", "."));
      const half = (Math.round(v * 50) / 100).toFixed(2).replace(".", ",");
      const rest = (v - Math.round(v * 50) / 100).toFixed(2).replace(".", ",");
      await parts.nth(0).fill(half); await sleep(500);
      await parts.nth(1).fill(rest); await sleep(500);
    }
    await sleep(800);
    // O botão de confirmar fica DENTRO da janela "Fechar comanda — ..." (os cartões também têm "Fechar R$").
    const modal = p.locator("div", { has: p.getByText(/^Fechar comanda/) }).filter({ has: p.getByRole("button", { name: "Voltar" }) }).last();
    await h.click(modal.getByRole("button", { name: /^Fechar R\$/ }), 2600);
  },

  async clientes(p, h) {
    await h.go("/customers", 900);
    h.markStart();
    await h.move(700, 300, 700);
    await sleep(800);
    await h.click(h.role("button", /^Em risco$/), 1300);
    await h.click(h.role("button", /^Sumido$/), 1300);
    await h.click(h.role("button", /Assinantes/), 1300);
    await h.go("/customers", 700);
    await h.click(p.locator("button.rw-open").nth(3), 2400);
    await h.wheel(500, 100, 60);
    await sleep(1500);
    await p.keyboard.press("Escape");
    await sleep(800);
  },

  async fidelizacao(p, h) {
    await h.go("/retention", 900);
    h.markStart();
    await h.move(800, 300, 700);
    await sleep(1500);
    await h.wheel(700, 100, 70);
    await sleep(1800);
    await h.go("/plans", 600);
    await h.move(700, 260, 700);
    await sleep(2400);
    await h.go("/packages", 600);
    await h.move(700, 260, 700);
    await sleep(2200);
    await h.go("/coupons", 600);
    await h.move(700, 300, 700);
    await sleep(2200);
  },

  async dinheiro(p, h) {
    await h.go("/caixa", 900);
    h.markStart();
    await h.to(h.text(/ESPERADO NA GAVETA/i), 800);
    await sleep(1600);
    await h.to(h.text(/POR FORMA DE PAGAMENTO/i), 700);
    await sleep(1500);
    await h.wheel(500, 100, 60);
    await sleep(1000);
    await h.go("/commissions", 600);
    await h.click(h.role("button", /Este mês/), 2200);
    await h.go("/commissions/settlement", 600);
    await h.move(800, 260, 700);
    await sleep(2000);
    await h.go("/expenses", 600);
    await h.to(h.text(/SOBROU/i), 800);
    await sleep(2200);
    await h.wheel(500, 100, 60);
    await h.go("/products", 600);
    await h.move(800, 260, 700);
    await sleep(2000);
  },

  async configurar(p, h) {
    await h.go("/services", 900);
    h.markStart();
    await h.move(800, 260, 700);
    await sleep(2200);
    await h.go("/professionals", 600);
    await h.move(800, 300, 700);
    await sleep(2400);
    await h.go("/settings", 800);
    await h.to(h.text(/Status do bot/i), 700);
    await sleep(1800);
    await h.to(h.text(/Saúde do sistema/i), 700);
    await sleep(2200);
    await h.click(p.getByRole("tab", { name: "Automações" }), 2200);
    await h.wheel(500, 100, 70);
    await sleep(2200);
    await h.wheel(500, 100, 70);
    await sleep(1800);
  },

  async site(p, h) {
    await h.go("/settings", 800);
    h.markStart();
    await h.click(p.getByRole("tab", { name: "Site" }), 2200);
    const tpl = p.locator("button[aria-pressed]").filter({ hasText: /Estúdio/ }).first();
    await h.click(tpl, 2600);
    const tpl2 = p.locator("button[aria-pressed]").filter({ hasText: /Vitrine/ }).first();
    await h.click(tpl2, 2600);
    const tpl3 = p.locator("button[aria-pressed]").filter({ hasText: /Clássico/ }).first();
    await h.click(tpl3, 1600);
    await h.go("/b/demo-navalha", 1500);
    await h.wheel(900, 60, 40);
    await sleep(800);
    await h.wheel(1400, 60, 40);
    await sleep(800);
    await h.go("/b/demo-navalha/agendar", 1400);
    await h.move(800, 400, 600);
    const opts = p.locator("main button, .bk button").filter({ hasNotText: /voltar/i });
    await h.click(opts.nth(0), 1400);
    await h.click(opts.nth(1), 1400);
    await h.click(opts.nth(2), 1600);
  },

  async papeis(p, h) {
    await h.go("/login", 600);
    await h.ctx.clearCookies();
    await h.go("/login", 800);
    h.markStart();
    await h.type('input[type="email"]', "recepcao@navalhademo.local", 30);
    await h.type('input[type="password"]', "demo12345", 30);
    await h.click('button[type="submit"]', 2200);
    await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 500 }).catch(() => {});
    await h.move(130, 300, 900);
    await sleep(1800);
    await h.click(h.role("button", /^Sair$/), 1200);
    await h.type('input[type="email"]', "barbeiro@navalhademo.local", 30);
    await h.type('input[type="password"]', "demo12345", 30);
    await h.click('button[type="submit"]', 2400);
    await p.getByRole("button", { name: /Pular tour/i }).click({ timeout: 500 }).catch(() => {});
    await h.move(800, 400, 900);
    await h.wheel(400, 100, 60);
    await sleep(1500);
  },

  async celular(p, h) {
    await h.go("/", 1200);
    h.markStart();
    await h.wheel(500, 80, 60);
    await sleep(600);
    await h.click(p.locator(".bottom-nav .bn-item").nth(1), 1400);
    await h.click(p.locator(".bottom-nav button.bn-item"), 1800);
  },

  async plataforma(p, h) {
    await h.go("/plataforma", 1200);
    h.markStart();
    await h.type('input[type="email"]', envVar("RAZOR_PLATFORM_ADMIN_EMAIL") || "admin@razor.local", 30);
    const pw = p.locator('input[type="password"]').first();
    await h.click(pw, 200);
    await pw.fill(envVar("RAZOR_PLATFORM_ADMIN_PASSWORD") || "");
    await sleep(400);
    await h.click(p.getByRole("button", { name: "Entrar" }), 2600);
    await h.move(800, 400, 700);
    await sleep(1600);
    // Fora da gravação: "IA" (mostra o fim da chave da OpenAI) e "Conversas" (mensagens reais).
    for (const tab of ["Saúde", "Métricas", "Trilha", "Segurança"]) {
      await h.click(p.getByRole("tab", { name: new RegExp(tab) }).or(p.getByRole("button", { name: new RegExp("^" + tab) })), 2600);
    }
  },
};

const MOBILE = { celular: { width: 390, height: 844, scale: 2, mobile: true } };

for (const [id, fn] of Object.entries(SCENES)) {
  if (only && !only.includes(id)) continue;
  console.log(`gravando ${id} (mín. ${min(id).toFixed(1)}s)`);
  const opts = { ...(id === "cadastro" || id === "plataforma" ? {} : ownerOpts), ...(MOBILE[id] || {}) };
  clips[id] = await record(OUT, id, min(id), fn, opts);
  fs.writeFileSync(clipsFile, JSON.stringify(clips, null, 1));
}
console.log("pronto");
