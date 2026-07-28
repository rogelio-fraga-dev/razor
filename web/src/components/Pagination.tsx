// Navegação entre páginas de uma lista.
//
// Deliberadamente simples: anterior / próxima + "página X de Y" + a contagem real de itens.
// Não numera todas as páginas porque com uma base de 3 mil clientes isso viraria 150 botões —
// e o dono não procura "a página 87", ele procura um nome (a busca resolve isso).
import type { PageMeta } from "../api/types";

interface Props {
  page: PageMeta;
  onChange: (page: number) => void;
  /** Nome do que está sendo listado, no plural. Ex.: "clientes", "contas". */
  label: string;
  /** Desabilita os botões enquanto uma página nova está carregando. */
  busy?: boolean;
}

export function Pagination({ page, onChange, label, busy }: Props) {
  // Uma página só não precisa de controle nenhum — mostrar "página 1 de 1" com dois botões
  // mortos é ruído. A contagem continua visível no cabeçalho de cada tela.
  if (page.totalPages <= 1) return null;

  const primeiro = page.page * page.size + 1;
  const ultimo = Math.min(primeiro + page.items.length - 1, page.total);

  return (
    <nav className="pagination" aria-label={`Navegação entre páginas de ${label}`}>
      <span className="pagination-info">
        {primeiro}–{ultimo} de {page.total} {label}
      </span>
      <div className="pagination-actions">
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => onChange(page.page - 1)}
          disabled={!page.hasPrevious || busy}
          aria-label="Página anterior"
        >
          ← Anterior
        </button>
        <span className="pagination-current mono" aria-current="page">
          {page.page + 1} / {page.totalPages}
        </span>
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => onChange(page.page + 1)}
          disabled={!page.hasNext || busy}
          aria-label="Próxima página"
        >
          Próxima →
        </button>
      </div>
    </nav>
  );
}
