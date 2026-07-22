// Cliente HTTP do painel. Anexa o JWT (Bearer), trata 401 (dispara logout) e
// desembrulha o corpo JSON. Base configuravel por VITE_API_URL (padrao: mesmo host).

const BASE = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");
const TOKEN_KEY = "razor.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

/** Erro de API com status e mensagem amigavel (do corpo {error} quando houver). */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// Callback disparado quando um 401 chega (token expirado/invalido) — usado pelo AuthContext.
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    onUnauthorized?.();
    throw new ApiError(401, "Sessão expirada. Faça login novamente.");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    const message = (data && (data.error || data.message)) || `Erro ${res.status}`;
    throw new ApiError(res.status, message);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),
};
