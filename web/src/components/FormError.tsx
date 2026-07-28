import type { ReactNode } from "react";

/**
 * Mensagem de erro de formulário, anunciada a leitor de tela.
 *
 * <p>Existe porque o painel tinha o mesmo `<div className="error">` copiado em catorze lugares,
 * sempre sem `role="alert"`. Sem ele o leitor de tela não percebe que a mensagem apareceu: a
 * pessoa clica em "Salvar", ouve silêncio e não sabe por que nada aconteceu. No login isso é
 * pior — senha errada some visualmente sem nenhum aviso.
 *
 * <p>O projeto já sabia fazer certo (`WeekdayPicker`, `AdminsEditor`); faltava um lugar só para
 * a regra morar.
 */
export function FormError({ children }: { children?: ReactNode }) {
  if (!children) return null;
  return (
    <div className="error" role="alert">
      {children}
    </div>
  );
}
