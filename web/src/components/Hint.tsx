// Ajuda em linguagem simples ao lado do jargão ("DRE", "Sangria"…).
//
// Por que um botão e não um title=: title não existe no toque (o dono usa o painel no celular)
// e leitor de tela nem sempre o anuncia. Aqui o "?" é um <button> de verdade:
//  - toque/clique abre e fecha; Esc fecha; tocar fora fecha;
//  - no desktop o hover/foco também mostra, sem precisar clicar;
//  - o texto está SEMPRE no DOM e ligado por aria-describedby, então o leitor de tela lê a
//    explicação junto com o botão mesmo com o balão fechado.
import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { GLOSSARY, type GlossaryTerm } from "../lib/glossary";

export function Hint({
  term,
  label,
  align = "center",
}: {
  term: GlossaryTerm;
  /** Nome do termo para o leitor de tela ("O que é DRE?"). Padrão: o próprio termo. */
  label?: string;
  /** Lado para onde o balão abre — "start" perto da borda esquerda, "end" perto da direita. */
  align?: "center" | "start" | "end";
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [hover, setHover] = useState(false);
  const rootRef = useRef<HTMLSpanElement>(null);
  const visible = open || hover;
  const btnRef = useRef<HTMLButtonElement>(null);
  // Posição fixa calculada do botão: o termo costuma morar em cabeçalho de .card ou célula de
  // tabela com overflow:hidden/auto, que cortaria um balão posicionado em absoluto.
  const [pos, setPos] = useState<CSSProperties | undefined>(undefined);
  useLayoutEffect(() => {
    if (!visible || !btnRef.current) return;
    const r = btnRef.current.getBoundingClientRect();
    const width = Math.min(260, window.innerWidth - 32);
    let left = align === "start" ? r.left - 6 : align === "end" ? r.right + 6 - width : r.left + r.width / 2 - width / 2;
    left = Math.max(16, Math.min(left, window.innerWidth - width - 16));
    const below = r.bottom + 8;
    setPos({ position: "fixed", top: below, left, width, transform: "none" });
  }, [visible, align]);

  // Balão em posição fixa não acompanha a rolagem: rolou, fecha.
  useEffect(() => {
    if (!visible) return;
    const close = () => {
      setOpen(false);
      setHover(false);
    };
    window.addEventListener("scroll", close, { capture: true, passive: true });
    window.addEventListener("resize", close);
    return () => {
      window.removeEventListener("scroll", close, { capture: true });
      window.removeEventListener("resize", close);
    };
  }, [visible]);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDown);
    return () => document.removeEventListener("pointerdown", onDown);
  }, [open]);

  return (
    <span
      className="hint"
      ref={rootRef}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
    >
      <button
        ref={btnRef}
        type="button"
        className="hint-btn"
        aria-label={`O que é ${label ?? term}?`}
        aria-describedby={id}
        aria-expanded={open}
        onClick={(e) => {
          // Dentro de um <label>/linha clicável o toque não pode disparar a ação do pai.
          e.preventDefault();
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        onFocus={() => setHover(true)}
        onBlur={() => setHover(false)}
        onKeyDown={(e) => {
          if (e.key === "Escape" && visible) {
            e.stopPropagation();
            setOpen(false);
            setHover(false);
          }
        }}
      >
        ?
      </button>
      <span
        id={id}
        role="tooltip"
        className={`hint-pop${align !== "center" ? ` align-${align}` : ""}`}
        hidden={!visible}
        style={pos}
      >
        {GLOSSARY[term]}
      </span>
    </span>
  );
}
