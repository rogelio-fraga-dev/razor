// Dedupe de GETs idênticos em voo.
//
// Componentes irmãos montam ao mesmo tempo e pedem o mesmo endpoint: no Dashboard, `Cockpit` e
// `ActivationChecklist` buscavam `/settings/bot/status` em paralelo, e a agenda do dia era pedida
// duas vezes. Enquanto uma requisição está em voo, as demais para o mesmo caminho aproveitam a
// mesma promise.
//
// Não é cache: a entrada sai do mapa assim que a promise assenta, então todo recarregamento bate
// no servidor de novo. Só elimina a duplicação simultânea — o que evita também que duas telas
// mostrem números diferentes por terem lido o banco em instantes diferentes.
import { api } from "../api/client";

const inFlight = new Map<string, Promise<unknown>>();

/** GET com dedupe pelo próprio caminho. Use nos endpoints buscados por mais de um componente. */
export function getOnce<T>(path: string): Promise<T> {
  const existing = inFlight.get(path) as Promise<T> | undefined;
  if (existing) return existing;

  const promise = api.get<T>(path).finally(() => inFlight.delete(path));
  inFlight.set(path, promise);
  return promise;
}
