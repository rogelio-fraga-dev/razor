import { useCallback, useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent, type RefObject } from "react";

const FOCUSABLE =
  'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

interface Options {
  /** Esc dentro do contêiner. Sem isto, Esc não é tratado aqui (quem chama cuida). */
  onEscape?: () => void;
  /** Quem recebe o foco ao abrir; padrão: o próprio contêiner (que precisa de tabIndex=-1). */
  initialFocus?: RefObject<HTMLElement>;
  /**
   * Para onde o foco volta ao fechar. Padrão: o elemento que tinha o foco antes de abrir.
   * Recebe esse elemento — útil quando o gatilho pode ter sido re-renderizado (ex.: o cartão
   * da agenda que mudou de lugar) e é preciso achar o substituto.
   */
  restoreFocus?: (previous: HTMLElement | null) => void;
}

/**
 * Diálogo acessível por teclado: o foco entra ao abrir, o Tab circula só dentro do contêiner,
 * Esc fecha e, ao desmontar, o foco volta para quem abriu. Usado pelo Modal, pela gaveta da
 * agenda e pelo lightbox do site.
 *
 * Devolve o `ref` do contêiner e o `onKeyDown` para pendurar nele — o handler fica no elemento
 * (não no window) para que diálogos empilhados só tratem o Esc do mais de cima.
 */
export function useFocusTrap<T extends HTMLElement = HTMLDivElement>(options: Options = {}) {
  const ref = useRef<T>(null);
  // Opções em ref: o efeito de foco roda só na montagem, mas sempre enxerga o callback atual.
  const opts = useRef(options);
  opts.current = options;

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    (opts.current.initialFocus?.current ?? ref.current)?.focus();
    return () => {
      const restore = opts.current.restoreFocus;
      if (restore) restore(previous);
      else previous?.focus?.();
    };
  }, []);

  const onKeyDown = useCallback((e: ReactKeyboardEvent) => {
    if (e.key === "Escape" && opts.current.onEscape) {
      e.stopPropagation();
      opts.current.onEscape();
      return;
    }
    const root = ref.current;
    if (e.key !== "Tab" || !root) return;

    const focusables = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE));
    if (focusables.length === 0) {
      e.preventDefault();
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement;
    // Foco fora da lista (no próprio contêiner, ou num título com tabIndex=-1): entra pela ponta.
    const outside = !focusables.includes(active as HTMLElement);
    if (e.shiftKey && (active === first || outside)) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && (active === last || outside)) {
      e.preventDefault();
      first.focus();
    }
  }, []);

  return { ref, onKeyDown };
}
