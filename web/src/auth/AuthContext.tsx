import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setToken, setUnauthorizedHandler, getToken } from "../api/client";
import type { LoginResponse } from "../api/types";

interface Session {
  token: string;
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

const STORAGE_KEY = "razor.session";
const AuthContext = createContext<AuthState | null>(null);

function loadSession(): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw || !getToken()) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession);

  const logout = useMemo(
    () => () => {
      setToken(null);
      localStorage.removeItem(STORAGE_KEY);
      setSession(null);
    },
    [],
  );

  useEffect(() => {
    setUnauthorizedHandler(logout);
  }, [logout]);

  const login = async (email: string, password: string) => {
    const res = await api.post<LoginResponse>("/api/auth/login", { email, password });
    setToken(res.token);
    const s: Session = {
      token: res.token,
      role: res.role,
      name: res.name,
      email: res.email,
      barbershopId: res.barbershopId,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    setSession(s);
  };

  const value = useMemo<AuthState>(() => ({ session, login, logout }), [session, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return ctx;
}
