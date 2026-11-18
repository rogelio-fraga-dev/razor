// Gaveta de detalhe (UX_BLUEPRINT §2.3: resumo → detalhe em gaveta → edição em modal).
// Lateral no desktop, folha de baixo no celular (≤760px). Foco preso, Esc fecha, toque fora
// fecha, e o foco volta para quem abriu — o mesmo contrato da gaveta da agenda.
import { useId, useRef, type ReactNode } from "react";
import { useFocusTrap } from "../lib/useFocusTrap";
import "./drawer.css";

export function Drawer({
  title,
  subtitle,
  onClose,
  children,
  className,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  className?: string;
}) {
  const titleId = useId();
  const titleRef = useRef<HTMLHeadingElement>(null);
  const { ref, onKeyDown } = useFocusTrap<HTMLElement>({ onEscape: onClose, initialFocus: titleRef });

  return (
    <div
      className="drawer-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <aside
        ref={ref}
        className={`drawer${className ? ` ${className}` : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <span className="drawer-grip" aria-hidden="true" />
        <div className="drawer-head">
          <div>
            <h3 id={titleId} ref={titleRef} tabIndex={-1}>
              {title}
            </h3>
            {subtitle && <p className="muted">{subtitle}</p>}
          </div>
          <button className="iconbtn" onClick={onClose} aria-label="Fechar">
            ✕
          </button>
        </div>
        <div className="drawer-body stack">{children}</div>
      </aside>
    </div>
  );
}
