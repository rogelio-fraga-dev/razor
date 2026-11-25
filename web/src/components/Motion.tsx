// Movimento do painel (Parte 6): números que contam até o valor e o carimbo de "pago".
//
// Regras: só transform/opacity; nada de conteúdo escondido esperando animação (o número
// final é o que o leitor de tela ouve); quem pede menos movimento vê o valor direto.
import { useEffect, useRef, useState } from "react";

const reducedMotion = () =>
  typeof window !== "undefined" && !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/**
 * Mostra `value` formatado, contando a partir do valor anterior (ou de zero na primeira vez)
 * em ~0,9 s com desaceleração. O texto acessível é sempre o valor final.
 */
export function CountUp({
  value,
  format,
  duration = 900,
}: {
  value: number;
  format: (n: number) => string;
  duration?: number;
}) {
  const [shown, setShown] = useState(() => (reducedMotion() ? value : 0));
  const from = useRef(reducedMotion() ? value : 0);

  useEffect(() => {
    if (reducedMotion() || !Number.isFinite(value)) {
      setShown(value);
      from.current = value;
      return;
    }
    const start = from.current;
    const t0 = performance.now();
    let raf = 0;
    const tick = (now: number) => {
      const k = Math.min(1, (now - t0) / duration);
      const eased = 1 - Math.pow(1 - k, 3);
      setShown(start + (value - start) * eased);
      if (k < 1) raf = requestAnimationFrame(tick);
      else from.current = value;
    };
    raf = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(raf);
      from.current = value;
    };
  }, [value, duration]);

  return (
    <>
      <span aria-hidden="true">{format(shown)}</span>
      <span className="sr-only">{format(value)}</span>
    </>
  );
}

/** Dispara o carimbo no centro da tela (ex.: comanda fechada). */
export function stamp(text: string, tone: "ok" | "info" = "ok") {
  window.dispatchEvent(new CustomEvent("razor:stamp", { detail: { text, tone } }));
}

/** Camada do carimbo: montada uma vez no Layout. */
export function StampLayer() {
  const [item, setItem] = useState<{ text: string; tone: string; id: number } | null>(null);
  useEffect(() => {
    let timer = 0;
    const on = (e: Event) => {
      if (reducedMotion()) return;
      const { text, tone } = (e as CustomEvent<{ text: string; tone: string }>).detail;
      setItem({ text, tone, id: Date.now() });
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setItem(null), 1400);
    };
    window.addEventListener("razor:stamp", on);
    return () => {
      window.removeEventListener("razor:stamp", on);
      window.clearTimeout(timer);
    };
  }, []);
  if (!item) return null;
  return (
    <div className="stamp-layer" aria-hidden="true">
      <span key={item.id} className={`stamp stamp-${item.tone}`}>
        {item.text}
      </span>
    </div>
  );
}
