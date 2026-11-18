import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setUnauthorizedHandler } from "../api/client";
import type { LoginResponse, Me } from "../api/types";

// Sessao do painel. O JWT vive num cookie HttpOnly que este codigo nunca ve; aqui ficam so os
// dados NAO secretos do usuario (nome, papel), em memoria. Ao recarregar a pagina a sessao e
// restaurada perguntando ao backend quem sou (/api/admin/me) — o cookie vai sozinho.

interface Session {
  role: LoginResponse["role"];
  name: string | null;
  email: string;
  barbershopId: string;
}

interface AuthState {
  session: Session | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

// Chaves da versao antiga (JWT no localStorage). Apagadas no boot para nao deixar um token
// valido esquecido no navegador de quem atualizou o painel.
const LEGACY_KEYS = ["razor.token", "razor.session"];

const AuthContext = createContext<AuthState | null>(null);

function toSession(u: LoginResponse | Me): Session {
  return { role: u.role, name: u.name, email: u.email, barbershopId: u.barbershopId };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  // O site publico (/b/:slug) nao tem sessao: nao vale segurar a pagina esperando o /me.
  const [restoring, setRestoring] = useState(() => !window.location.pathname.startsWith("/b/"));

  // 401 em qualquer chamada: o cookie expirou ou foi invalidado — so limpa o estado local.
  const expire = useCallback(() => setSession(null), []);

  const logout = useCallback(() => {
    setSession(null);
    // Best-effort: mesmo se falhar (rede, CSRF), o estado local ja saiu.
    api.post("/api/auth/logout", undefined, { ignore401: true }).catch(() => {});
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(expire);
  }, [expire]);

  useEffect(() => {
    try {
      LEGACY_KEYS.forEach((k) => localStorage.removeItem(k));
    } catch {
      /* storage bloqueado: nada a limpar */
    }
    if (!restoring) return;
    let alive = true;
    api
      .get<Me>("/api/admin/me")
      .then((me) => alive && setSession(toSession(me)))
      .catch(() => alive && setSession(null))
      .finally(() => alive && setRestoring(false));
    return () => {
      alive = false;
    };
    // Roda uma vez, no boot.
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<LoginResponse>("/api/auth/login", { email, password }, { ignore401: true });
    setSession(toSession(res));
  }, []);

  const value = useMemo<AuthState>(() => ({ session, login, logout }), [session, login, logout]);

  // Enquanto o /me nao responde nao da para saber se ha sessao; renderizar agora mandaria quem
  // esta logado para /login e perderia a rota que ele abriu.
  if (restoring) return null;
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return ctx;
}
