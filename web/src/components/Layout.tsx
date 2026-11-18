import { useState, type ReactNode } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { applyTheme, currentTheme, type Theme } from "../lib/theme";
import { areaOf, visibleAreas, type AreaId, type NavArea } from "../lib/nav";
import { EdgeMark, RazorLogo } from "./Logo";
import { Clock } from "./Clock";
import { Drawer } from "./Drawer";
import { IconChevron } from "./Icons";

const COLLAPSED_KEY = "razor.sidebar_collapsed";
const CLOSED_GROUPS_KEY = "razor.nav_closed";

function readClosedGroups(): AreaId[] {
  try {
    const raw = localStorage.getItem(CLOSED_GROUPS_KEY);
    return raw ? (JSON.parse(raw) as AreaId[]) : [];
  } catch {
    return [];
  }
}

function tourId(to: string): string {
  return `nav-${to.replace(/^\//, "").replace(/\//g, "-") || "home"}`;
}

/**
 * Navegação inferior do celular (≤760px): as 5 áreas, sempre a 1 toque. Área com uma página só
 * (Agenda) navega direto; área com várias abre uma folha com as páginas dela — qualquer tela
 * fica a no máximo 2 toques (regra dos 2 cliques, UX_BLUEPRINT §5).
 */
function BottomNav({ areas }: { areas: NavArea[] }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [sheet, setSheet] = useState<NavArea | null>(null);
  const current = areaOf(pathname);

  const open = (a: NavArea) => {
    if (a.pages.length === 1) navigate(a.pages[0].to);
    else setSheet(a);
  };

  return (
    <>
      <nav className="bottom-nav" aria-label="Áreas do painel">
        {areas.map((a) => {
          const active = current === a.id;
          return (
            <button
              key={a.id}
              type="button"
              className={`bn-item${active ? " active" : ""}`}
              aria-current={active ? "page" : undefined}
              aria-haspopup={a.pages.length > 1 ? "dialog" : undefined}
              data-tour={a.id === "clientes" ? "nav-customers" : a.id === "configurar" ? "nav-settings" : undefined}
              onClick={() => open(a)}
            >
              <span className="bn-ico">{a.icon}</span>
              <span className="bn-lbl">{a.label}</span>
            </button>
          );
        })}
      </nav>
      {sheet && (
        <Drawer title={sheet.label} onClose={() => setSheet(null)} className="area-sheet">
          <ul className="area-list">
            {sheet.pages.map((p) => (
              <li key={p.to}>
                <NavLink
                  to={p.to}
                  end={p.end}
                  className={({ isActive }) => `area-link${isActive ? " active" : ""}`}
                  onClick={() => setSheet(null)}
                >
                  <span className="area-ico">{p.icon}</span>
                  <span className="area-text">
                    <span className="area-label">{p.label}</span>
                    <span className="area-hint">{p.hint}</span>
                  </span>
                  <span className="area-go" aria-hidden="true">›</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </Drawer>
      )}
    </>
  );
}

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
  const { pathname } = useLocation();
  const [theme, setTheme] = useState<Theme>(currentTheme);
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(COLLAPSED_KEY) === "true";
    } catch {
      return false;
    }
  });
  const [closed, setClosed] = useState<AreaId[]>(readClosedGroups);

  const toggleSidebar = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(COLLAPSED_KEY, String(next));
      } catch {
        /* preferência só desta aba */
      }
      return next;
    });
  };

  const toggleGroup = (id: AreaId) => {
    setClosed((prev) => {
      const next = prev.includes(id) ? prev.filter((g) => g !== id) : [...prev, id];
      try {
        localStorage.setItem(CLOSED_GROUPS_KEY, JSON.stringify(next));
      } catch {
        /* idem */
      }
      return next;
    });
  };

  const toggleTheme = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    setTheme(next);
    applyTheme(next);
  };

  const initial = (session?.name || session?.email || "?").charAt(0).toUpperCase();

  // Menu filtrado pelo papel (lib/nav + lib/access): área sem página visível some inteira.
  const areas = visibleAreas(session?.role);
  const currentArea = areaOf(pathname);

  return (
    <div className={`app${collapsed ? " sidebar-collapsed" : ""}`}>
      <aside className="sidebar">
        <div className="brand" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
          <RazorLogo size={24} />
          <button
            className="iconbtn sidebar-toggle-btn"
            onClick={toggleSidebar}
            title={collapsed ? "Expandir menu lateral" : "Recolher menu lateral"}
            aria-label={collapsed ? "Expandir menu lateral" : "Recolher menu lateral"}
            aria-expanded={!collapsed}
            style={{ width: 28, height: 28, padding: 0, fontSize: 13, flexShrink: 0 }}
          >
            {collapsed ? "⇥" : "⇤"}
          </button>
        </div>
        <span className="brand-tag mono">SISTEMA OPERACIONAL</span>
        <nav className="nav" aria-label="Menu principal">
          {areas.map((a) => {
            // Área com a página atual nunca fica fechada: o menu sempre mostra onde a pessoa está.
            const isOpen = !closed.includes(a.id) || currentArea === a.id;
            const single = a.pages.length === 1;
            const listId = `nav-group-${a.id}`;
            return (
              <div className={`nav-group${isOpen ? "" : " closed"}${single ? " single" : ""}`} key={a.id}>
                {single ? (
                  <span className="nav-label">{a.label}</span>
                ) : (
                  <button
                    type="button"
                    className="nav-label nav-group-toggle"
                    aria-expanded={isOpen}
                    aria-controls={listId}
                    onClick={() => toggleGroup(a.id)}
                    disabled={currentArea === a.id}
                    title={currentArea === a.id ? "Você está nesta área" : undefined}
                  >
                    <span>{a.label}</span>
                    <span className="nav-chev" aria-hidden="true">
                      <IconChevron />
                    </span>
                  </button>
                )}
                <div className="nav-items" id={listId}>
                  {a.pages.map((n) => (
                    <NavLink
                      key={n.to}
                      to={n.to}
                      end={n.end}
                      title={n.label}
                      data-tour={tourId(n.to)}
                      className={({ isActive }) => (isActive ? "active" : "")}
                    >
                      <span className="ico">{n.icon}</span>
                      <span className="lbl">{n.label}</span>
                    </NavLink>
                  ))}
                </div>
              </div>
            );
          })}
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
          <span className="topbar-mark" aria-hidden="true">
            <EdgeMark size={22} color="currentColor" />
          </span>
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

      <BottomNav areas={areas} />
    </div>
  );
}
