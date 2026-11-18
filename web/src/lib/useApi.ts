import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../api/client";

interface State<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/**
 * GET com estado de carregamento/erro e recarregamento. `deps` reexecuta a busca.
 *
 * Para endpoints buscados por mais de um componente na mesma tela, use `getOnce` de
 * `lib/dedupe` dentro do fetcher — o dedupe fica na requisição, não no hook, e assim vale
 * também para chamadas agrupadas em `Promise.all`.
 */
export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[] = []): State<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // `deps` é a lista de reatividade declarada por quem chama — o fetcher é sempre uma arrow
  // nova a cada render, então usá-lo como dependência recriaria o efeito em todo render.
  // Quem chama precisa incluir em `deps` tudo que o fetcher captura (ex.: o id da entidade).
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fetcher, deps);

  // Cada busca ganha um número; só a mais recente pode gravar dado/erro/loading. O contador é
  // compartilhado entre o efeito (troca de `deps`) e o `reload()` manual — assim uma resposta
  // lenta de uma busca antiga nunca sobrescreve a de uma busca mais nova, venha de onde vier.
  const requestId = useRef(0);

  const load = useCallback(() => {
    const id = ++requestId.current;
    const isLatest = () => id === requestId.current;
    setLoading(true);
    setError(null);
    run()
      .then((d) => {
        if (isLatest()) setData(d);
      })
      .catch((e) => {
        if (isLatest()) setError(e instanceof ApiError ? e.message : "Erro ao carregar.");
      })
      .finally(() => {
        if (isLatest()) setLoading(false);
      });
  }, [run]);

  useEffect(() => {
    load();
    // Desmontou (ou `deps` mudou): invalida a busca em voo.
    const counter = requestId;
    return () => {
      counter.current++;
    };
  }, [load]);

  return { data, loading, error, reload: load };
}
