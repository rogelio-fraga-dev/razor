// Cliente HTTP do painel. A sessao e um cookie HttpOnly emitido pelo backend no login: este
// codigo NUNCA ve o JWT (nem guarda nada no localStorage), entao um XSS nao tem token para levar.
// Como o navegador anexa o cookie sozinho, toda escrita leva o token CSRF (double-submit): o
// valor do cookie XSRF-TOKEN, legivel pelo JS, vai no header X-XSRF-TOKEN.
//
// Painel e API precisam estar na MESMA ORIGEM (proxy do vite em dev, nginx no Docker): o cookie
// de sessao e SameSite=Strict e o XSRF-TOKEN so e legivel pelo JS da propria origem. Por isso
// VITE_API_URL deve ficar vazio; ele so existe para casos de mesma origem com prefixo.

const BASE = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Erro de API com status, mensagem amigavel (do corpo {error} quando houver) e o codigo
 * maquina do corpo ({code}, ex.: "CSRF") — quem precisa decidir algo olha o codigo, nao o texto.
 */
export class ApiError extends Error {
  constructor(public status: number, message: string, public code: string | null = null) {
    super(message);
  }
}

// Callback disparado quando um 401 chega (sessao expirada/invalida) — usado pelo AuthContext.
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const c = part.trim();
    if (c.startsWith(prefix)) return decodeURIComponent(c.slice(prefix.length));
  }
  return null;
}

/**
 * Token CSRF atual. O backend emite o cookie em qualquer chamada do painel; se ainda nao houver
 * (primeira escrita da aba, ou logo apos o logout), um GET em /api/auth/csrf o produz.
 */
async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  return refreshCsrfToken();
}

/** Pede um token novo ao backend (o cookie pode ter girado ou expirado) e o rele. */
async function refreshCsrfToken(): Promise<string | null> {
  await fetch(`${BASE}/api/auth/csrf`, { credentials: "include" });
  return readCookie(CSRF_COOKIE);
}

interface RequestOptions {
  /** Nao dispara o logout global no 401 (ex.: senha errada no login nao e "sessao expirada"). */
  ignore401?: boolean;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  opts: RequestOptions = {},
  csrfRetried = false,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (!SAFE_METHODS.has(method)) {
    const token = csrfRetried ? readCookie(CSRF_COOKIE) : await csrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && !opts.ignore401) {
    onUnauthorized?.();
    throw new ApiError(401, "Sessão expirada. Faça login novamente.");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: { error?: string; message?: string; code?: string } | undefined;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = undefined;
  }

  if (!res.ok) {
    const code = data && typeof data.code === "string" ? data.code : null;
    // Token CSRF velho (cookie girou, aba aberta ha dias): renova uma vez e repete a escrita.
    // So uma — se o backend recusar de novo, o problema nao e o token e o erro sobe.
    if (res.status === 403 && code === "CSRF" && !SAFE_METHODS.has(method) && !csrfRetried) {
      await refreshCsrfToken();
      return request<T>(method, path, body, opts, true);
    }
    const message = (data && (data.error || data.message)) || `Erro ${res.status}`;
    throw new ApiError(res.status, message, code);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) => request<T>("GET", path, undefined, opts),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>("POST", path, body, opts),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>("PUT", path, body, opts),
  patch: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>("PATCH", path, body, opts),
  del: <T>(path: string, opts?: RequestOptions) => request<T>("DELETE", path, undefined, opts),
};
