import { useState, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { applyTheme, currentTheme, type Theme } from "../lib/theme";
import { RazorLogo } from "./Logo";
import { IconGem, IconGrid, IconPercent, IconSliders, IconTag, IconUser, IconUsers } from "./Icons";

const NAV = [
  { to: "/", label: "Painel", icon: <IconGrid />, end: true },
  { to: "/services", label: "Serviços", icon: <IconTag /> },
  { to: "/professionals", label: "Profissionais", icon: <IconUser /> },
  { to: "/customers", label: "Clientes", icon: <IconUsers /> },
  { to: "/plans", label: "Planos", icon: <IconGem /> },
  { to: "/commissions", label: "Comissões", icon: <IconPercent /> },
  { to: "/settings", label: "Configurações", icon: <IconSliders /> },
];

export function Layout({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
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
        <span className="nav-label">Menu</span>
        <nav className="nav">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              title={n.label}
              className={({ isActive }) => (isActive ? "active" : "")}
            >
              <span className="ico">{n.icon}</span>
              <span className="lbl">{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip">
            <span className="avatar">{initial}</span>
            <div className="grow user-meta">
              <div className="user-name">{session?.name || "Admin"}</div>
              <div className="faint user-mail">{session?.email}</div>
            </div>
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <h1>{title}</h1>
          <div className="row">
            {actions}
            <button className="iconbtn" onClick={toggleTheme} title="Alternar tema" aria-label="Alternar tema">
              {theme === "dark" ? "☀" : "☾"}
            </button>
            <button className="btn btn-ghost btn-sm" onClick={logout}>
              Sair
            </button>
          </div>
        </header>
        <div className="content">{children}</div>
      </div>
    </div>
  );
}
