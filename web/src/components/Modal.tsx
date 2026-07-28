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
  size = "md",
  onRequestClose,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** `wide` para formulários com colunas, QR code e o assistente de configuração. */
  size?: "md" | "wide";
  /**
   * Interceptador de fechamento por clique fora, Esc e ✕ — se devolver `false`, o diálogo
   * NÃO fecha. Existe para quem tem trabalho não salvo dentro: o assistente de configuração
   * só grava no último passo, e um clique fora do diálogo descartava tudo em silêncio.
   */
  onRequestClose?: () => boolean;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);

  const tryClose = () => {
    if (onRequestClose && !onRequestClose()) return;
    onClose();
  };

  // Foco entra no diálogo ao abrir e volta para onde estava ao fechar — sem isso, quem navega
  // por teclado continua "na página de trás" depois que o modal aparece.
  //
  // O foco vai para o TÍTULO, não para o primeiro focável. O primeiro focável no DOM é sempre o
  // botão ✕ (ele vem antes do corpo), então o primeiro Enter de quem navega por teclado fechava
  // o diálogo sem querer. Focar o título também faz o leitor de tela anunciar do que se trata.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    (titleRef.current ?? dialogRef.current)?.focus();
    return () => previouslyFocused?.focus?.();
  }, []);

  // Esc fecha; Tab circula dentro do diálogo em vez de vazar para o resto da página.
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      tryClose();
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
    <div className="modal-overlay" onMouseDown={tryClose}>
      <div
        ref={dialogRef}
        className={`modal${size === "wide" ? " modal-wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <div className="modal-head">
          <h3 id={titleId} ref={titleRef} tabIndex={-1} style={{ outline: "none" }}>
            {title}
          </h3>
          <button className="iconbtn" onClick={tryClose} aria-label="Fechar">
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
