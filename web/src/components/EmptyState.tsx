// Estados vazios que GUIAM (UX_BLUEPRINT §5): em vez de "nenhum dado", dizemos qual é
// o próximo passo — o momento vazio é onde o usuário mais precisa de direção.
import type { ReactNode } from "react";

export function EmptyState({
  icon = "◇",
  title,
  hint,
  action,
}: {
  icon?: ReactNode;
  title: string;
  hint?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state empty-blueprint">
      <div className="empty-icon">{icon}</div>
      <p className="es-title">{title}</p>
      {hint && <p className="es-hint">{hint}</p>}
      {action && <div className="es-action" style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
}
