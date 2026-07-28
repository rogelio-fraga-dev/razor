import { useState, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { canAccess } from "../lib/access";
import { applyTheme, currentTheme, type Theme } from "../lib/theme";
import { RazorLogo } from "./Logo";
import { Clock } from "./Clock";
import { IconBox, IconHandshake, IconTicket, IconReceipt, IconCalendar, IconCash, IconGem, IconGrid, IconPercent, IconSliders, IconPulse, IconTag, IconTrendDown, IconUser, IconUsers } from "./Icons";

// Navegação agrupada pelo que o dono faz — não por "telas do sistema".
const NAV_GROUPS = [
  {
    label: "Operação",
    items: [
      { to: "/", label: "Painel", icon: <IconGrid />, end: true },
      { to: "/agenda", label: "Agenda", icon: <IconCalendar /> },
      { to: "/customers", label: "Clientes", icon: <IconUsers /> },
      { to: "/retention", label: "Retenção", icon: <IconPulse /> },
    ],
  },
  {
    label: "Sua barbearia",
    items: [
      { to: "/services", label: "Serviços", icon: <IconTag /> },
      { to: "/products", label: "Produtos", icon: <IconBox /> },
      { to: "/professionals", label: "Profissionais", icon: <IconUser /> },
      { to: "/plans", label: "Planos", icon: <IconGem /> },
      { to: "/coupons", label: "Cupons", icon: <IconTicket /> },
    ],
  },
  {
    label: "Dinheiro",
    items: [
      { to: "/comandas", label: "Comandas", icon: <IconReceipt /> },
      { to: "/caixa", label: "Caixa", icon: <IconCash /> },
      { to: "/expenses", label: "Despesas", icon: <IconTrendDown /> },
      { to: "/commissions", label: "Comissões", icon: <IconPercent /> },
      { to: "/commissions/settlement", label: "Acerto", icon: <IconHandshake /> },
    ],
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
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    return localStorage.getItem("razor.sidebar_collapsed") === "true";
  });

  const toggleSidebar = () => {
    setCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem("razor.sidebar_collapsed", String(next));
      return next;
    });
  };

  const toggleTheme = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    setTheme(next);
    applyTheme(next);
  };

  const initial = (session?.name || session?.email || "?").charAt(0).toUpperCase();

  // Menu filtrado pelo papel, com o grupo inteiro sumindo quando nada dentro dele sobra —
  // senão a recepção veria o rótulo "Dinheiro" sobre um espaço vazio.
  const visibleGroups = NAV_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((n) => canAccess(n.to, session?.role)),
  })).filter((g) => g.items.length > 0);

  return (
    <div className={`app${collapsed ? " sidebar-collapsed" : ""}`}>
      <aside className="sidebar">
        <div className="brand" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
          <RazorLogo size={24} />
          <button
            className="iconbtn sidebar-toggle-btn"
            onClick={toggleSidebar}
            title={collapsed ? "Expandir menu lateral" : "Recolher menu lateral"}
            aria-label="Recolher/Expandir menu"
            style={{ width: 28, height: 28, padding: 0, fontSize: 13, flexShrink: 0 }}
          >
            {collapsed ? "⇥" : "⇤"}
          </button>
        </div>
        <span className="brand-tag mono">SISTEMA OPERACIONAL</span>
        <nav className="nav">
          {visibleGroups.map((g) => (
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

      <div className={`main${flush ? " main-flush" : ""}`}>
        <header className="topbar">
          <h1>{title}</h1>
          <div className="row">
            {actions}
            <Clock />
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
