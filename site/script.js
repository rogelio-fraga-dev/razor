// RAZOR · landing — navbar, reveals, demo de chat, toggle de preços.
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

// Navbar: glass -> sólida ao rolar
const nav = document.getElementById("nav");
const onScroll = () => nav.classList.toggle("solid", scrollY > 40);
addEventListener("scroll", onScroll, { passive: true });
onScroll();

// Reveal on scroll
const io = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (e.isIntersecting) {
      e.target.classList.add("in");
      io.unobserve(e.target);
    }
  }
}, { threshold: 0.12 });
document.querySelectorAll(".rv").forEach((el) => io.observe(el));

// ---- Demo de chat no hero (loop) ----
const chat = document.getElementById("chat");
const typing = document.getElementById("typing");
const SCRIPT = [
  { who: "c", text: "tem horário amanhã no fim do dia? 🙏" },
  { who: "b", text: "Tenho! Amanhã: <b>18h30</b> ou <b>19h</b> com o Diego. Qual prefere?" },
  { who: "c", text: "19h. corte e barba" },
  { who: "b", text: "Agendado ✅ <b>Sex · 19h · Corte + Barba (R$ 60)</b>. Te lembro 1h antes! 👊", meta: "⚡ respondido pela IA em 2,3s" },
];

function addMsg({ who, text, meta }) {
  const el = document.createElement("div");
  el.className = `msg ${who}`;
  el.innerHTML = text + (meta ? `<span class="meta">${meta}</span>` : "");
  chat.appendChild(el);
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add("show")));
}

async function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function playChat() {
  if (reduced) { SCRIPT.forEach(addMsg); return; }
  // pequena espera pro hero entrar
  await sleep(900);
  for (;;) {
    chat.innerHTML = "";
    for (const m of SCRIPT) {
      if (m.who === "b") {
        typing.hidden = false;
        await sleep(1000);
        typing.hidden = true;
      } else {
        await sleep(700);
      }
      addMsg(m);
      await sleep(1300);
    }
    await sleep(5200);
  }
}
if (chat) playChat();

// ---- Toggle mensal/anual (mostra o equivalente mensal E o total do ano) ----
const tgM = document.getElementById("tg-m");
const tgA = document.getElementById("tg-a");
const plans = document.querySelectorAll(".plan");
function setPeriod(annual) {
  tgM.classList.toggle("active", !annual);
  tgA.classList.toggle("active", annual);
  plans.forEach((plan) => {
    const val = plan.querySelector(".val");
    const note = plan.querySelector(".p-total");
    if (!val) return;
    val.textContent = "R$ " + val.dataset[annual ? "a" : "m"];
    if (note) {
      if (annual) {
        const total = val.dataset.total;
        note.textContent = `R$ ${total} à vista no ano · economize 2 meses`;
        note.hidden = false;
      } else {
        note.hidden = true;
      }
    }
  });
}
tgM?.addEventListener("click", () => setPeriod(false));
tgA?.addEventListener("click", () => setPeriod(true));

// ---- Tema claro/escuro da landing ----
const THEME_KEY = "razor.site.theme";
const themeBtn = document.getElementById("theme-toggle");
function applySiteTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  localStorage.setItem(THEME_KEY, t);
  if (themeBtn) themeBtn.textContent = t === "light" ? "☾" : "☀";
}
applySiteTheme(localStorage.getItem(THEME_KEY) || "dark");
themeBtn?.addEventListener("click", () => {
  const cur = document.documentElement.getAttribute("data-theme");
  applySiteTheme(cur === "light" ? "dark" : "light");
});

// ---- Contadores animados (números do hero) ----
const counters = document.querySelectorAll("[data-count]");
const cio = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    const el = e.target;
    cio.unobserve(el);
    const target = parseFloat(el.dataset.count);
    const suffix = el.dataset.suffix || "";
    const prefix = el.dataset.prefix || "";
    if (reduced) { el.textContent = prefix + target.toLocaleString("pt-BR") + suffix; continue; }
    const t0 = performance.now();
    const tick = (t) => {
      const p = Math.min((t - t0) / 1100, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      el.textContent = prefix + Math.round(target * eased).toLocaleString("pt-BR") + suffix;
      if (p < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }
}, { threshold: 0.5 });
counters.forEach((c) => cio.observe(c));

// ---- Hover 3D sutil nos cards do bento ----
if (!reduced && matchMedia("(hover: hover)").matches) {
  document.querySelectorAll(".b-card, .tpl, .plan").forEach((card) => {
    card.addEventListener("mousemove", (e) => {
      const r = card.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width - 0.5;
      const py = (e.clientY - r.top) / r.height - 0.5;
      card.style.transform = `perspective(900px) rotateX(${-py * 3}deg) rotateY(${px * 3}deg) translateY(-3px)`;
    });
    card.addEventListener("mouseleave", () => { card.style.transform = ""; });
  });
}

// ---- Ligação com o painel (login e planos) ----------------------------------
// O endereço do painel muda por ambiente: em desenvolvimento ele roda no Vite (:5173),
// em produção fica sob o mesmo domínio, em /app. Fica centralizado aqui porque estava
// cravado no HTML como localhost — o que só funcionava na máquina de quem desenvolve.
const PANEL_URL = (() => {
  const host = location.hostname;
  const isDev = host === "localhost" || host === "127.0.0.1" || host === "";
  return isDev ? "http://localhost:5173" : "/app";
})();

// "Entrar" e qualquer outro link marcado com data-panel apontam para o painel.
document.querySelectorAll("[data-panel]").forEach((el) => {
  el.setAttribute("href", PANEL_URL);
});

// CTAs dos planos: levam ao painel já dizendo qual plano o visitante escolheu.
// Enquanto o cadastro self-service não existe (Fase 4), o painel recebe o parâmetro e
// orienta o visitante — melhor do que o href="#" atual, que não fazia nada.
document.querySelectorAll("[data-cta]").forEach((el) => {
  el.setAttribute("href", `${PANEL_URL}/?plano=${encodeURIComponent(el.dataset.cta)}`);
});
