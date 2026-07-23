// Tour de primeiro acesso (UX_BLUEPRINT §3.1): 5 spotlights de ~20s no total, "Pular"
// sempre visível. Dispara uma única vez (localStorage) no primeiro login do painel.
import { useCallback, useEffect, useState } from "react";

const DONE_KEY = "razor.tour_done";

export interface TourStep {
  target: string; // seletor CSS (data-tour="...")
  title: string;
  text: string;
}

export function isTourDone(): boolean {
  return localStorage.getItem(DONE_KEY) === "1";
}

export function markTourDone() {
  localStorage.setItem(DONE_KEY, "1");
}

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

export function Tour({ steps, onFinish }: { steps: TourStep[]; onFinish: () => void }) {
  const [index, setIndex] = useState(0);
  const [rect, setRect] = useState<Rect | null>(null);

  const step = steps[index];

  const measure = useCallback(() => {
    const el = document.querySelector(step.target);
    if (!el) {
      setRect(null);
      return;
    }
    el.scrollIntoView({ block: "center", behavior: "instant" as ScrollBehavior });
    const r = el.getBoundingClientRect();
    setRect({ top: r.top - 6, left: r.left - 6, width: r.width + 12, height: r.height + 12 });
  }, [step.target]);

  useEffect(() => {
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [measure]);

  const finish = () => {
    markTourDone();
    onFinish();
  };

  const next = () => (index < steps.length - 1 ? setIndex(index + 1) : finish());

  // Sem alvo na tela (ex.: layout mobile diferente): pula o passo silenciosamente.
  useEffect(() => {
    if (rect === null) {
      const el = document.querySelector(step.target);
      if (!el) next();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rect]);

  if (!rect) return null;

  // Tooltip abaixo do alvo quando couber; acima caso contrário.
  const below = rect.top + rect.height + 190 < window.innerHeight;
  const tipTop = below ? rect.top + rect.height + 14 : Math.max(12, rect.top - 176);
  const tipLeft = Math.min(Math.max(12, rect.left), Math.max(12, window.innerWidth - 332));

  return (
    <div className="tour-root" role="dialog" aria-label={`Tour: ${step.title}`}>
      <div
        className="tour-spot"
        style={{ top: rect.top, left: rect.left, width: rect.width, height: rect.height }}
      />
      <div className="tour-tip" style={{ top: tipTop, left: tipLeft }}>
        <span className="tour-count mono">{index + 1} / {steps.length}</span>
        <h4>{step.title}</h4>
        <p>{step.text}</p>
        <div className="row between" style={{ marginTop: 14 }}>
          <button className="btn btn-ghost btn-sm" onClick={finish}>Pular tour</button>
          <button className="btn btn-primary btn-sm" onClick={next}>
            {index < steps.length - 1 ? "Próximo" : "Começar a usar"}
          </button>
        </div>
      </div>
    </div>
  );
}
