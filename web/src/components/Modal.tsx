import { useEffect, useId, useRef, type ReactNode } from "react";

/**
 * Diálogo base de todo o painel — cadastro de produto, serviço, cupom, plano, abertura de
 * caixa, comanda, vale de comissão. Por ser compartilhado, acessibilidade aqui vale por
 * dezenas de fluxos: sem isto, leitor de tela não anuncia o diálogo, o Tab escapa para a
 * página atrás do overlay e o Esc não fecha.
 */
export function Modal({
  title,
  onClose,
  children,
  footer,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  // Foco entra no diálogo ao abrir e volta para onde estava ao fechar — sem isso, quem navega
  // por teclado continua "na página de trás" depois que o modal aparece.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const first = dialogRef.current?.querySelector<HTMLElement>(
      'input, select, textarea, button, [href], [tabindex]:not([tabindex="-1"])',
    );
    (first ?? dialogRef.current)?.focus();
    return () => previouslyFocused?.focus?.();
  }, []);

  // Esc fecha; Tab circula dentro do diálogo em vez de vazar para o resto da página.
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      onClose();
      return;
    }
    if (e.key !== "Tab" || !dialogRef.current) return;

    const focusables = Array.from(
      dialogRef.current.querySelectorAll<HTMLElement>(
        'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
      ),
    );
    if (focusables.length === 0) return;

    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <div className="modal-head">
          <h3 id={titleId}>{title}</h3>
          <button className="iconbtn" onClick={onClose} aria-label="Fechar">
            ✕
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <label className="switch">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span className="track" />
      <span>{label}</span>
    </label>
  );
}
