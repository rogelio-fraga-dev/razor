// Movimento do site — revelação ao rolar, efeitos ligados à rolagem, inclinação 3D, botão
// magnético e títulos que sobem palavra por palavra. Tudo em CSS/SVG + requestAnimationFrame:
// nenhuma dependência nova no bundle.
//
// Regras que todo efeito daqui segue:
//   - só anima transform/opacity (60 fps no celular);
//   - trabalho contínuo pausa fora da tela (IntersectionObserver);
//   - conteúdo NUNCA depende de JS para aparecer: o estado "escondido" só existe depois que o
//     observador está armado (`data-armed`), e o que já está na tela nasce visível;
//   - três níveis: "full" (tudo), "subtle" (só revelações suaves) e "off" (estático). O visitante
//     com `prefers-reduced-motion` sempre cai em "off", seja qual for a escolha do dono.
import {
  Fragment,
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react";
import type { SiteMotion } from "../api/types";

const MotionContext = createContext<SiteMotion>("full");

const prefersReduced = () =>
  typeof window !== "undefined" && !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

const finePointer = () => typeof window !== "undefined" && !!window.matchMedia?.("(hover: hover) and (pointer: fine)").matches;

/** O nível efetivo: a escolha do dono, rebaixada a "off" se o visitante pede menos movimento. */
export function useEffectiveMotion(owner: SiteMotion): SiteMotion {
  const [reduced, setReduced] = useState(prefersReduced);
  useEffect(() => {
    const mq = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    if (!mq) return;
    const on = () => setReduced(mq.matches);
    mq.addEventListener?.("change", on);
    return () => mq.removeEventListener?.("change", on);
  }, []);
  return reduced ? "off" : owner;
}

export function MotionProvider({ value, children }: { value: SiteMotion; children: ReactNode }) {
  return <MotionContext.Provider value={value}>{children}</MotionContext.Provider>;
}

export const useMotion = () => useContext(MotionContext);

/* ---------------------------------------------------------------------------
   Revelação ao rolar.
   ------------------------------------------------------------------------- */

const inViewport = (el: Element) => {
  const r = el.getBoundingClientRect();
  return r.top < window.innerHeight && r.bottom > 0;
};

/**
 * Marca `[data-reveal]` com `data-shown` quando entra na tela. Roda a cada render (o editor
 * troca seções ao vivo): observa os que ainda não apareceram. useLayoutEffect = antes da pintura, então o
 * que já está visível nunca pisca escondido.
 */
export function useReveal(root: RefObject<HTMLElement>, motion: SiteMotion) {
  useLayoutEffect(() => {
    const el = root.current;
    if (!el) return;
    if (motion === "off" || !("IntersectionObserver" in window)) {
      el.removeAttribute("data-armed");
      return;
    }
    // Um observador por render: barato, e não guarda estado entre montagens (o StrictMode monta
    // duas vezes — estado em ref deixaria itens escondidos para sempre).
    const io = new IntersectionObserver(
      (entries) =>
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.setAttribute("data-shown", "");
            io.unobserve(e.target);
          }
        }),
      { rootMargin: "0px 0px -6% 0px", threshold: 0.06 },
    );
    el.querySelectorAll("[data-reveal]:not([data-shown])").forEach((item) => {
      if (inViewport(item)) item.setAttribute("data-shown", "");
      else io.observe(item);
    });
    el.setAttribute("data-armed", "");
    return () => io.disconnect();
  });
}

/* ---------------------------------------------------------------------------
   Visibilidade: pausa animações contínuas (poste, anel de fotos, faixa) fora da tela.
   ------------------------------------------------------------------------- */

export function usePlayWhenVisible<T extends HTMLElement>(): RefObject<T> {
  const ref = useRef<T>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || !("IntersectionObserver" in window)) return;
    const io = new IntersectionObserver(([e]) => el.setAttribute("data-play", e.isIntersecting ? "1" : "0"));
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return ref;
}

/* ---------------------------------------------------------------------------
   Progresso de rolagem de um bloco → variável CSS `--sp` (0 no topo, 1 quando sai da tela).
   Só escuta a rolagem enquanto o bloco está visível.
   ------------------------------------------------------------------------- */

export function useScrollVar<T extends HTMLElement>(enabled: boolean, mode: "leave" | "through" = "leave"): RefObject<T> {
  const ref = useRef<T>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || !enabled) return;
    let raf = 0;
    let visible = true;
    const update = () => {
      raf = 0;
      const r = el.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      // "leave": 0 com o topo do bloco no topo da tela, 1 quando ele sai por cima.
      // "through": 0 quando o bloco entra por baixo, 1 quando sai por cima.
      const p = mode === "leave" ? -r.top / Math.max(1, r.height) : (vh - r.top) / (vh + r.height);
      el.style.setProperty("--sp", Math.min(1, Math.max(0, p)).toFixed(4));
    };
    const onScroll = () => {
      if (visible && !raf) raf = requestAnimationFrame(update);
    };
    const io =
      "IntersectionObserver" in window
        ? new IntersectionObserver(([e]) => {
            visible = e.isIntersecting;
            if (visible) onScroll();
          })
        : null;
    io?.observe(el);
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      io?.disconnect();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (raf) cancelAnimationFrame(raf);
      el.style.removeProperty("--sp");
    };
  }, [enabled, mode]);
  return ref;
}

/** Fio fino no topo da página que acompanha a leitura. */
export function ScrollMeter() {
  const motion = useMotion();
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el || motion !== "full") return;
    let raf = 0;
    const update = () => {
      raf = 0;
      const max = document.documentElement.scrollHeight - window.innerHeight;
      el.style.transform = `scaleX(${max > 0 ? Math.min(1, window.scrollY / max) : 0})`;
    };
    const on = () => {
      if (!raf) raf = requestAnimationFrame(update);
    };
    update();
    window.addEventListener("scroll", on, { passive: true });
    window.addEventListener("resize", on);
    return () => {
      window.removeEventListener("scroll", on);
      window.removeEventListener("resize", on);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [motion]);
  if (motion !== "full") return null;
  return <div ref={ref} className="rz-meter" aria-hidden="true" />;
}

/* ---------------------------------------------------------------------------
   Inclinação 3D que segue o ponteiro — delegada no contêiner, vale para cada `[data-tilt]`.
   ------------------------------------------------------------------------- */

export function useTilt<T extends HTMLElement>(): RefObject<T> {
  const ref = useRef<T>(null);
  const motion = useMotion();
  useEffect(() => {
    const root = ref.current;
    if (!root || motion !== "full" || !finePointer()) return;
    let raf = 0;
    let last: { el: HTMLElement; x: number; y: number } | null = null;
    const apply = () => {
      raf = 0;
      if (!last) return;
      const { el, x, y } = last;
      const r = el.getBoundingClientRect();
      const px = (x - r.left) / r.width - 0.5;
      const py = (y - r.top) / r.height - 0.5;
      el.style.setProperty("--rx", `${(-py * 9).toFixed(2)}deg`);
      el.style.setProperty("--ry", `${(px * 11).toFixed(2)}deg`);
      el.style.setProperty("--gx", `${((px + 0.5) * 100).toFixed(1)}%`);
      el.style.setProperty("--gy", `${((py + 0.5) * 100).toFixed(1)}%`);
      el.setAttribute("data-tilting", "");
    };
    const reset = (el: HTMLElement) => {
      el.style.removeProperty("--rx");
      el.style.removeProperty("--ry");
      el.removeAttribute("data-tilting");
    };
    const move = (e: PointerEvent) => {
      const el = (e.target as HTMLElement).closest<HTMLElement>("[data-tilt]");
      if (last && last.el !== el) reset(last.el);
      if (!el || !root.contains(el)) {
        last = null;
        return;
      }
      last = { el, x: e.clientX, y: e.clientY };
      if (!raf) raf = requestAnimationFrame(apply);
    };
    const leave = () => {
      if (last) reset(last.el);
      last = null;
    };
    root.addEventListener("pointermove", move);
    root.addEventListener("pointerleave", leave);
    return () => {
      root.removeEventListener("pointermove", move);
      root.removeEventListener("pointerleave", leave);
      if (raf) cancelAnimationFrame(raf);
    };
  }, [motion]);
  return ref;
}

/* ---------------------------------------------------------------------------
   Botão magnético: o CTA "puxa" o ponteiro alguns pixels. Só com mouse e movimento completo.
   ------------------------------------------------------------------------- */

export function Magnetic({ children, strength = 0.28 }: { children: ReactNode; strength?: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const motion = useMotion();
  useEffect(() => {
    const el = ref.current;
    if (!el || motion !== "full" || !finePointer()) return;
    let raf = 0;
    let tx = 0;
    let ty = 0;
    const paint = () => {
      raf = 0;
      el.style.transform = `translate3d(${tx.toFixed(1)}px, ${ty.toFixed(1)}px, 0)`;
    };
    const move = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      tx = (e.clientX - (r.left + r.width / 2)) * strength;
      ty = (e.clientY - (r.top + r.height / 2)) * strength;
      if (!raf) raf = requestAnimationFrame(paint);
    };
    const leave = () => {
      tx = 0;
      ty = 0;
      if (!raf) raf = requestAnimationFrame(paint);
    };
    el.addEventListener("pointermove", move);
    el.addEventListener("pointerleave", leave);
    return () => {
      el.removeEventListener("pointermove", move);
      el.removeEventListener("pointerleave", leave);
      if (raf) cancelAnimationFrame(raf);
      el.style.transform = "";
    };
  }, [motion, strength]);
  return (
    <span ref={ref} className="rz-magnet">
      {children}
    </span>
  );
}

/* ---------------------------------------------------------------------------
   Título que sobe palavra por palavra, por trás de uma máscara.
   O texto continua um texto só para leitores de tela (cada palavra é um span inline).
   ------------------------------------------------------------------------- */

export function SplitWords({ text, delay = 0 }: { text: string; delay?: number }) {
  const words = text.split(/\s+/).filter(Boolean);
  return (
    <>
      {words.map((w, i) => (
        // O espaço fica FORA do inline-block: dentro dele, o navegador o descarta no fim da caixa.
        <Fragment key={i}>
          <span className="rz-w">
            <span style={{ ["--i" as string]: i + delay }}>{w}</span>
          </span>
          {i < words.length - 1 ? " " : null}
        </Fragment>
      ))}
    </>
  );
}
