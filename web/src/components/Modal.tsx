import { useId, useRef, type ReactNode } from "react";
import { useFocusTrap } from "../lib/useFocusTrap";

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
  const titleRef = useRef<HTMLHeadingElement>(null);

  const tryClose = () => {
    if (onRequestClose && !onRequestClose()) return;
    onClose();
  };

  // Foco entra no diálogo ao abrir e volta para onde estava ao fechar; Esc fecha; Tab circula
  // dentro do diálogo em vez de vazar para a página atrás do overlay.
  //
  // O foco vai para o TÍTULO, não para o primeiro focável. O primeiro focável no DOM é sempre o
  // botão ✕ (ele vem antes do corpo), então o primeiro Enter de quem navega por teclado fechava
  // o diálogo sem querer. Focar o título também faz o leitor de tela anunciar do que se trata.
  const { ref: dialogRef, onKeyDown } = useFocusTrap<HTMLDivElement>({
    onEscape: tryClose,
    initialFocus: titleRef,
  });

  return (
    // Clique fora fecha (atalho de mouse; no teclado o equivalente é Esc ou o ✕). O overlay é
    // só apresentação — o diálogo de verdade é o filho com role="dialog".
    <div
      className="modal-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) tryClose();
      }}
    >
      {/* onKeyDown no diálogo é o padrão WAI-ARIA de modal (Esc + Tab preso). */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        ref={dialogRef}
        className={`modal${size === "wide" ? " modal-wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
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
