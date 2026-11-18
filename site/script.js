// RAZOR · landing — navbar, reveals, demo do WhatsApp, painel animado, calculadora,
// toggle de preços, tema e ligação com o painel.
//
// Sem dependências e sem script inline (a CSP só aceita script-src 'self'). Tudo que
// anima mexe só em transform/opacity e desliga com prefers-reduced-motion.
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- Navbar: transparente -> sólida ao rolar; menu no celular --------------
const nav = $("#nav");
const onNavScroll = () => nav.classList.toggle("solid", scrollY > 24);
addEventListener("scroll", onNavScroll, { passive: true });
onNavScroll();

const menuBtn = $("#menu-btn");
const navLinks = $("#nav-links");
function setMenu(open) {
  navLinks.classList.toggle("open", open);
  menuBtn.setAttribute("aria-expanded", String(open));
  menuBtn.setAttribute("aria-label", open ? "Fechar menu" : "Abrir menu");
  nav.classList.toggle("solid", open || scrollY > 24);
}
menuBtn?.addEventListener("click", () => setMenu(!navLinks.classList.contains("open")));
navLinks?.addEventListener("click", (e) => { if (e.target.closest("a")) setMenu(false); });
addEventListener("keydown", (e) => { if (e.key === "Escape") setMenu(false); });

// ---- Reveal ao rolar -------------------------------------------------------
const io = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (e.isIntersecting) {
      e.target.classList.add("in");
      io.unobserve(e.target);
    }
  }
}, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
$$(".rv").forEach((el) => io.observe(el));

// ---- Tema claro/escuro -----------------------------------------------------
const THEME_KEY = "razor.site.theme";
const store = {
  get() { try { return localStorage.getItem(THEME_KEY); } catch { return null; } },
  set(v) { try { localStorage.setItem(THEME_KEY, v); } catch { /* modo privado */ } },
};
function applySiteTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  $('meta[name="theme-color"]')?.setAttribute("content", t === "light" ? "#F6F7F9" : "#0A0A0C");
}
applySiteTheme(store.get() || "dark");
$("#theme-toggle")?.addEventListener("click", () => {
  const next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
  applySiteTheme(next);
  store.set(next);
});

// ---- Demo do WhatsApp no hero ---------------------------------------------
// Roteiro: áudio do cliente -> IA oferece horários -> cliente escolhe -> agendamento
// confirmado -> sinal por PIX -> pago. Os cartões flutuantes acendem em sincronia.
const chat = $("#chat");
const waSub = $("#wa-sub");
const narrow = matchMedia("(max-width: 520px)");
const floats = { ai: $("#f-ai"), agenda: $("#f-agenda"), pix: $("#f-pix") };
const WAVE = [0.3, 0.5, 0.8, 0.6, 0.9, 0.4, 0.7, 1, 0.6, 0.35, 0.55, 0.85, 0.5, 0.3, 0.65, 0.9, 0.45, 0.3, 0.6, 0.4];

const SCRIPT = [
  { who: "out", t: "19:42", audio: true,
    html: `<div class="audio"><span class="av">VC</span><span class="play"></span><span class="wave">${WAVE.map((h) => `<i style="--h:${h}"></i>`).join("")}<span class="track"><span class="head"></span></span></span></div><span class="audio-dur">0:07</span>` },
  { who: "in", t: "19:42", typing: 1300, float: "ai",
    html: `<span class="transcript"><b>Transcrição:</b> “fala, tem horário amanhã depois das 18h? corte e barba”</span>Tenho sim! Amanhã com o <b>Diego</b>, que faz corte e barba:<div class="slots"><span>18h30</span><span data-pick>19h</span><span>19h30</span></div>` },
  { who: "out", t: "19:43", html: "19h fechou 👊", pick: true },
  { who: "in", t: "19:43", typing: 1200, float: "agenda",
    html: `<div class="confirm"><span class="c-head">✅ AGENDADO</span><div class="c-row"><span>Quando</span>Qui · 19h</div><div class="c-row"><span>Serviço</span>Corte + Barba</div><div class="c-row"><span>Barbeiro</span>Diego</div><div class="c-row"><span>Valor</span>R$ 60,00</div></div>` },
  { who: "in", t: "19:43", typing: 900,
    html: `Pra garantir seu horário, o sinal é <b>R$ 15</b> (abatido no dia):<div class="pixbox"><b>PIX copia e cola</b>00020126580014br.gov.bcb.pix…5204000053039865802BR</div>` },
  { who: "out", t: "19:44", html: "pago ✅" },
  { who: "in", t: "19:44", typing: 900, float: "pix",
    html: "Sinal recebido, tá garantido! Amanhã te mando a confirmação. Até lá 🤝" },
];

function bubble(m) {
  const el = document.createElement("div");
  el.className = `msg ${m.who}`;
  el.innerHTML = `${m.html}<span class="t">${m.t}</span>`;
  return el;
}
function show(el) {
  chat.appendChild(el);
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add("show")));
}
// Mantém só o que cabe na tela: o resto já "subiu" e sai do DOM.
function trim() {
  while (chat.children.length > 6) chat.firstElementChild.remove();
}

async function playChat() {
  if (!chat) return;
  if (reduced) {
    SCRIPT.slice(1).forEach((m) => { const el = bubble(m); el.classList.add("show"); chat.appendChild(el); });
    $("[data-pick]", chat)?.classList.add("pick");
    (narrow.matches ? [floats.pix] : Object.values(floats)).forEach((f) => f?.classList.add("on"));
    return;
  }
  await sleep(700);
  for (;;) {
    chat.innerHTML = "";
    Object.values(floats).forEach((f) => f?.classList.remove("on"));
    for (const m of SCRIPT) {
      if (m.typing) {
        const typing = bubble({ who: "in", html: "<i></i><i></i><i></i>", t: "" });
        typing.classList.add("typing");
        typing.querySelector(".t").remove();
        waSub.textContent = "digitando…";
        waSub.classList.add("typing-on");
        show(typing);
        await sleep(m.typing);
        typing.remove();
        waSub.textContent = "online";
        waSub.classList.remove("typing-on");
      } else {
        await sleep(600);
      }
      if (m.pick) $("[data-pick]", chat)?.classList.add("pick");
      show(bubble(m));
      trim();
      if (m.float && floats[m.float]) {
        // Em tela estreita não há espaço em volta do telefone: um cartão de cada vez.
        if (narrow.matches) Object.values(floats).forEach((f) => f?.classList.remove("on"));
        floats[m.float].classList.add("on");
      }
      await sleep(m.audio ? 2700 : 1500);
    }
    await sleep(4200);
  }
}
playChat();

// Inclinação do palco 3D seguindo o ponteiro (só em desktop com mouse).
const stage = $("#stage");
const stageIn = $("#stage-in");
if (stage && !reduced && matchMedia("(hover: hover) and (min-width: 961px)").matches) {
  stage.addEventListener("pointermove", (e) => {
    const r = stage.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - 0.5;
    const py = (e.clientY - r.top) / r.height - 0.5;
    stageIn.style.setProperty("--ry", `${-14 + px * 16}deg`);
    stageIn.style.setProperty("--rx", `${6 - py * 10}deg`);
  });
  stage.addEventListener("pointerleave", () => {
    stageIn.style.removeProperty("--ry");
    stageIn.style.removeProperty("--rx");
  });
}

// ---- Animações guiadas pela rolagem ----------------------------------------
// Uma única passada por frame: o painel "deita" e endireita ao entrar na tela, e a
// linha do fluxo da IA vai sendo preenchida.
const mock = $("#mock");
const mockStage = $("#mock-stage");
const flow = $("#flow");
const flowFill = $("#flow-fill");
const clamp01 = (v) => Math.max(0, Math.min(1, v));

function measureCol() {
  const cols = $$(".ag-col", mock).filter((c) => c.offsetParent !== null);
  if (cols.length >= 2) {
    const d = cols[1].getBoundingClientRect().left - cols[0].getBoundingClientRect().left;
    mock.style.setProperty("--colw", `${d}px`);
  }
}

let ticking = false;
function onScrollFrame() {
  ticking = false;
  const vh = innerHeight;
  if (mock && mockStage) {
    const r = mockStage.getBoundingClientRect();
    // t = 1 quando o topo do painel está no pé da tela; 0 quando chegou a 25% do topo
    const t = clamp01((r.top - vh * 0.2) / (vh * 0.7));
    mock.style.setProperty("--t", t.toFixed(3));
  }
  if (flow && flowFill) {
    const r = flow.getBoundingClientRect();
    const p = clamp01((vh * 0.75 - r.top) / (r.height + vh * 0.2));
    flowFill.style.setProperty("--p", p.toFixed(3));
  }
}
function requestFrame() {
  if (!ticking) { ticking = true; requestAnimationFrame(onScrollFrame); }
}
if (!reduced) {
  addEventListener("scroll", requestFrame, { passive: true });
  addEventListener("resize", requestFrame);
  onScrollFrame();
} else {
  mock?.style.setProperty("--t", "0");
  flowFill?.style.setProperty("--p", "1");
}

// O painel só anima enquanto está visível (economiza bateria no celular).
if (mock) {
  measureCol();
  addEventListener("resize", measureCol);
  new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (e.isIntersecting) mock.classList.add("seen");
      mock.classList.toggle("play", e.isIntersecting && !reduced);
    }
  }, { threshold: 0.25 }).observe(mock);
}

// ---- Calculadora de faltas -------------------------------------------------
const brl = (v) => "R$ " + Math.round(v).toLocaleString("pt-BR");
const calcInputs = {
  cortes: $("#c-cortes"), ticket: $("#c-ticket"), faltas: $("#c-faltas"), rec: $("#c-rec"),
};
function paintRange(el) {
  const pct = ((el.value - el.min) / (el.max - el.min)) * 100;
  el.style.setProperty("--pct", `${pct}%`);
}
function calc() {
  const { cortes, ticket, faltas, rec } = calcInputs;
  if (!cortes) return;
  const n = +cortes.value, tk = +ticket.value, pf = +faltas.value / 100, pr = +rec.value / 100;
  const qtdFaltas = Math.round(n * pf);
  const perdida = qtdFaltas * tk;
  const recuperada = perdida * pr;
  $("#o-cortes").textContent = n.toLocaleString("pt-BR");
  $("#o-ticket").textContent = brl(tk);
  $("#o-faltas").textContent = `${faltas.value}%`;
  $("#o-rec").textContent = `${rec.value}%`;
  $("#r-faltas").textContent = qtdFaltas.toLocaleString("pt-BR");
  $("#r-perdida").textContent = brl(perdida);
  $("#r-mes").innerHTML = `${brl(recuperada)}<small>/mês</small>`;
  $("#r-ano").textContent = `≈ ${brl(recuperada * 12)} em 12 meses`;
  Object.values(calcInputs).forEach(paintRange);
}
Object.values(calcInputs).forEach((el) => el?.addEventListener("input", calc));
$("#calc")?.addEventListener("submit", (e) => e.preventDefault());
calc();

// ---- Toggle mensal/anual (mostra o equivalente mensal E o total do ano) ----
const tgM = $("#tg-m");
const tgA = $("#tg-a");
function setPeriod(annual) {
  tgM.classList.toggle("active", !annual);
  tgA.classList.toggle("active", annual);
  tgM.setAttribute("aria-pressed", String(!annual));
  tgA.setAttribute("aria-pressed", String(annual));
  $$(".plan").forEach((plan) => {
    const val = $(".val", plan);
    const note = $(".p-total", plan);
    if (!val) return;
    val.textContent = "R$ " + val.dataset[annual ? "a" : "m"];
    if (note) {
      note.textContent = annual ? `R$ ${val.dataset.total} à vista no ano · economize 2 meses` : "";
      note.hidden = !annual;
    }
  });
}
tgM?.addEventListener("click", () => setPeriod(false));
tgA?.addEventListener("click", () => setPeriod(true));

// ---- Inclinação sutil nos cartões (desktop) --------------------------------
if (!reduced && matchMedia("(hover: hover)").matches) {
  $$(".b-card, .plan").forEach((card) => {
    card.addEventListener("pointermove", (e) => {
      const r = card.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width - 0.5;
      const py = (e.clientY - r.top) / r.height - 0.5;
      card.style.transform = `perspective(900px) rotateX(${-py * 3}deg) rotateY(${px * 3}deg) translateY(-3px)`;
    });
    card.addEventListener("pointerleave", () => { card.style.transform = ""; });
  });
}

// ---- Ligação com o painel (login e planos) ----------------------------------
// O endereço do painel muda por ambiente: Vite em dev (:5173), container próprio no
// Docker (:3000), domínio próprio em produção. Vem do config.js, que o container reescreve
// a partir de RAZOR_PANEL_URL — cravar aqui só funcionava na máquina de quem desenvolve.
//
// O fallback existe para quem abre o index.html direto do disco, sem servidor: aí não há
// container para gerar o config.js.
const PANEL_URL = (() => {
  const configured = window.RAZOR_CONFIG?.panelUrl;
  if (configured) return configured;
  const host = location.hostname;
  const isDev = host === "localhost" || host === "127.0.0.1" || host === "";
  return isDev ? "http://localhost:5173" : "/app";
})();

// "Entrar" e qualquer outro link marcado com data-panel apontam para o painel.
$$("[data-panel]").forEach((el) => {
  el.setAttribute("href", PANEL_URL);
});

// CTAs dos planos: levam ao painel já dizendo qual plano o visitante escolheu.
// Enquanto o cadastro self-service não existe (Fase 4), o painel recebe o parâmetro e
// orienta o visitante.
$$("[data-cta]").forEach((el) => {
  el.setAttribute("href", `${PANEL_URL}/?plano=${encodeURIComponent(el.dataset.cta)}`);
});
