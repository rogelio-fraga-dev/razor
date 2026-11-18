// Erro de carregamento padrão (UX_BLUEPRINT §5): diz o que houve em português e oferece o
// próximo passo — "Tentar de novo" — em vez de deixar a pessoa recarregar a página inteira.
export function ErrorState({
  message,
  onRetry,
  compact,
}: {
  message: string;
  onRetry?: () => void;
  /** Dentro de um card pequeno: menos respiro. */
  compact?: boolean;
}) {
  return (
    <div className="error-state" role="alert" style={compact ? { padding: "16px 14px" } : undefined}>
      <span className="es-icon" aria-hidden="true">⚠</span>
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className="btn btn-ghost btn-sm" onClick={onRetry}>
          Tentar de novo
        </button>
      ) : (
        <span className="es-sub">Confira a internet e tente de novo em instantes.</span>
      )}
    </div>
  );
}
