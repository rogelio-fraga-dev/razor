// Peças pequenas do editor do site: grupo recolhível, campo de texto com contador.
import { useId, type ReactNode } from "react";

/**
 * Grupo recolhível do editor. `<details>` nativo: abre e fecha com teclado e leitor de tela sem
 * nenhum ARIA a mais, e funciona igual no celular.
 */
export function Group({
  title,
  summary,
  open,
  onToggle,
  children,
}: {
  title: string;
  /** Resumo de uma linha do que está dentro — o dono vê o estado sem abrir. */
  summary?: ReactNode;
  open: boolean;
  onToggle: (open: boolean) => void;
  children: ReactNode;
}) {
  return (
    <details className="se-group" open={open} onToggle={(e) => onToggle((e.currentTarget as HTMLDetailsElement).open)}>
      <summary>
        <span className="se-group-title">{title}</span>
        {summary && <span className="se-group-sum">{summary}</span>}
        <span className="se-chevron" aria-hidden="true" />
      </summary>
      <div className="se-group-body">{children}</div>
    </details>
  );
}

/** Contador de caracteres. Fica âmbar perto do limite — o campo já não deixa passar dele. */
export function Counter({ value, max }: { value: string; max: number }) {
  const n = value.length;
  const cls = n >= max ? "se-count full" : n >= max * 0.85 ? "se-count near" : "se-count";
  return (
    <span className={cls} aria-hidden="true">
      {n}/{max}
    </span>
  );
}

/** Campo de texto com rótulo, contador, dica e erro inline (ligados ao campo para leitor de tela). */
export function TextField({
  label,
  value,
  onChange,
  onBlur,
  max,
  placeholder,
  multiline,
  rows = 3,
  hint,
  error,
  inputMode,
  mono,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onBlur?: () => void;
  max?: number;
  placeholder?: string;
  multiline?: boolean;
  rows?: number;
  hint?: ReactNode;
  error?: string | null;
  inputMode?: "text" | "tel" | "url";
  mono?: boolean;
}) {
  const id = useId();
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-err` : null].filter(Boolean).join(" ") || undefined;
  const common = {
    className: `input${error ? " input-invalid" : ""}${mono ? " mono" : ""}`,
    value,
    placeholder,
    maxLength: max,
    onBlur,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": describedBy,
  } as const;
  return (
    <div className="field">
      <label>
        <span className="se-label-row">
          <span>{label}</span>
          {max ? <Counter value={value} max={max} /> : null}
        </span>
        {multiline ? (
          <textarea {...common} rows={rows} onChange={(e) => onChange(e.target.value)} style={{ resize: "vertical", fontFamily: "inherit" }} />
        ) : (
          <input {...common} inputMode={inputMode} onChange={(e) => onChange(e.target.value)} />
        )}
      </label>
      {hint && (
        <span id={`${id}-hint`} className="se-hint">
          {hint}
        </span>
      )}
      {error && (
        <span id={`${id}-err`} className="se-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
