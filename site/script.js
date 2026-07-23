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

// ---- Toggle mensal/anual ----
const tgM = document.getElementById("tg-m");
const tgA = document.getElementById("tg-a");
const vals = document.querySelectorAll(".p-price .val");
function setPeriod(annual) {
  tgM.classList.toggle("active", !annual);
  tgA.classList.toggle("active", annual);
  vals.forEach((v) => {
    v.textContent = "R$ " + v.dataset[annual ? "a" : "m"];
  });
}
tgM?.addEventListener("click", () => setPeriod(false));
tgA?.addEventListener("click", () => setPeriod(true));
