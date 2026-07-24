import { useState, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { applyTheme, currentTheme, type Theme } from "../lib/theme";
import { RazorLogo } from "./Logo";
import { IconCalendar, IconGem, IconGrid, IconPercent, IconSliders, IconTag, IconUser, IconUsers } from "./Icons";

// Navegação agrupada pelo que o dono faz — não por "telas do sistema".
const NAV_GROUPS = [
  {
    label: "Operação",
    items: [
      { to: "/", label: "Painel", icon: <IconGrid />, end: true },
      { to: "/agenda", label: "Agenda", icon: <IconCalendar /> },
      { to: "/customers", label: "Clientes", icon: <IconUsers /> },
    ],
  },
  {
    label: "Sua barbearia",
    items: [
      { to: "/services", label: "Serviços", icon: <IconTag /> },
      { to: "/professionals", label: "Profissionais", icon: <IconUser /> },
      { to: "/plans", label: "Planos", icon: <IconGem /> },
    ],
  },
  {
    label: "Dinheiro",
    items: [{ to: "/commissions", label: "Comissões", icon: <IconPercent /> }],
  },
  {
    label: "Sistema",
    items: [{ to: "/settings", label: "Configurações", icon: <IconSliders /> }],
  },
];

export function Layout({
  title,
  actions,
  children,
  flush,
}: {
  title: string;
  actions?: ReactNode;
  children: ReactNode;
  /** true = a página ocupa toda a área útil, sem padding nem caixa (agenda, listas). */
  flush?: boolean;
}) {
  const { session, logout } = useAuth();
  const [theme, setTheme] = useState<Theme>(currentTheme);

  const toggleTheme = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    setTheme(next);
    applyTheme(next);
  };

  const initial = (session?.name || session?.email || "?").charAt(0).toUpperCase();

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <RazorLogo size={24} />
        </div>
        <span className="brand-tag mono">SISTEMA OPERACIONAL</span>
        <nav className="nav">
          {NAV_GROUPS.map((g) => (
            <div className="nav-group" key={g.label}>
              <span className="nav-label">{g.label}</span>
              {g.items.map((n) => (
                <NavLink
                  key={n.to}
                  to={n.to}
                  end={n.end}
                  title={n.label}
                  data-tour={`nav-${n.to.replace("/", "") || "home"}`}
                  className={({ isActive }) => (isActive ? "active" : "")}
                >
                  <span className="ico">{n.icon}</span>
                  <span className="lbl">{n.label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <NavLink to="/profile" className="user-chip" title="Meu perfil" data-tour="profile">
            <span className="avatar">{initial}</span>
            <div className="grow user-meta">
              <div className="user-name">{session?.name || "Admin"}</div>
              <div className="faint user-mail">{session?.email}</div>
            </div>
          </NavLink>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <h1>{title}</h1>
          <div className="row">
            {actions}
            <button
              className="kbd-hint"
              onClick={() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "k", ctrlKey: true }))}
              title="Barra de comandos"
            >
              <span>Buscar</span>
              <kbd>Ctrl K</kbd>
            </button>
            <button className="iconbtn" onClick={toggleTheme} title="Alternar tema" aria-label="Alternar tema">
              {theme === "dark" ? "☀" : "☾"}
            </button>
            <button className="btn btn-ghost btn-sm" onClick={logout}>
              Sair
            </button>
          </div>
        </header>
        <div className={`content${flush ? " flush" : ""}`}>{children}</div>
      </div>
    </div>
  );
}
