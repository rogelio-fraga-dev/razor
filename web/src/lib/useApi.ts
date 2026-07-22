import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";

interface State<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/** GET com estado de carregamento/erro e recarregamento. `deps` reexecuta a busca. */
export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[] = []): State<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fetcher, deps);

  const load = useCallback(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    run()
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(e instanceof ApiError ? e.message : "Erro ao carregar."))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [run]);

  useEffect(() => load(), [load]);

  return { data, loading, error, reload: load };
}
