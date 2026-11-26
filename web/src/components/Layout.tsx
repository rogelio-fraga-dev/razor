import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, useLocation } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { applyTheme, currentTheme, type Theme } from "../lib/theme";
import { api } from "../api/client";
import {
  featureEnabled,
  setDisabledFeatures,
  groupOf,
  pathMatches,
  visibleGroups,
  visibleQuick,
  type GroupId,
  type NavGroup,
  type NavPage,
} from "../lib/nav";
import { EdgeMark, RazorLogo } from "./Logo";
import { Clock } from "./Clock";
import { InstallAppButton } from "./InstallAppButton";
import { TrialBanner } from "./TrialBanner";
import { FeedbackPrompt } from "./FeedbackPrompt";
import { HealthBanner } from "./SystemHealth";
import { Drawer } from "./Drawer";
import { IconChevron, IconMore } from "./Icons";
import { StampLayer } from "./Motion";

const COLLAPSED_KEY = "razor.sidebar_collapsed";
/** Grupos abertos/fechados, POR USUÁRIO: dono e recepção dividem o computador do balcão. */
const OPEN_GROUPS_KEY = "razor.nav_open";
/** Mesma quebra do CSS (index.css): abaixo disso a barra lateral vira trilho de ícones. */
const RAIL_QUERY = "(max-width: 860px)";

type OpenMap = Partial<Record<GroupId, boolean>>;

function readOpenGroups(user: string): OpenMap {
  try {
    const raw = localStorage.getItem(`${OPEN_GROUPS_KEY}.${user}`);
    const parsed: unknown = raw ? JSON.parse(raw) : null;
    return parsed && typeof parsed === "object" ? (parsed as OpenMap) : {};
  } catch {
    return {};
  }
}

function writeOpenGroups(user: string, map: OpenMap) {
  try {
    localStorage.setItem(`${OPEN_GROUPS_KEY}.${user}`, JSON.stringify(map));
  } catch {
    /* storage bloqueado: a preferência vale só nesta aba */
  }
}

function useMedia(query: string): boolean {
  const get = () => (typeof window !== "undefined" && window.matchMedia ? window.matchMedia(query).matches : false);
  const [match, setMatch] = useState(get);
  useEffect(() => {
    if (!window.matchMedia) return;
    const mq = window.matchMedia(query);
    const on = () => setMatch(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, [query]);
  return match;
}

function tourId(to: string): string {
  return `nav-${to.replace(/^\//, "").replace(/\//g, "-") || "home"}`;
}

function NavItem({ page, sub }: { page: NavPage; sub?: boolean }) {
  return (
    <NavLink
      viewTransition
      to={page.to}
      end={page.end}
      title={page.label}
      data-tour={tourId(page.to)}
      className={({ isActive }) => `${sub ? "nav-subitem" : "nav-item"}${isActive ? " active" : ""}`}
    >
      {!sub && <span className="ico">{page.icon}</span>}
      <span className="lbl">{page.label}</span>
    </NavLink>
  );
}

/**
 * Menu do trilho de ícones (barra recolhida ou tablet): o grupo vira UM ícone e as páginas
 * abrem num painel ao lado. Esc fecha e devolve o foco ao ícone; clique fora também fecha.
 */
function GroupFlyout({
  group,
  id,
  anchor,
  onClose,
}: {
  group: NavGroup;
  id: string;
  anchor: HTMLElement;
  onClose: (returnFocus: boolean) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [top, setTop] = useState(() => anchor.getBoundingClientRect().top);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    // Não deixa o painel passar do pé da tela (o grupo Configurar fica lá embaixo).
    const r = anchor.getBoundingClientRect();
    const max = window.innerHeight - el.offsetHeight - 12;
    setTop(Math.max(12, Math.min(r.top - 8, max)));
    el.querySelector<HTMLElement>("a")?.focus();
  }, [anchor]);

  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!ref.current?.contains(t) && !anchor.contains(t)) onClose(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose(true);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [anchor, onClose]);

  return (
    <div
      ref={ref}
      id={id}
      className="nav-flyout"
      role="group"
      aria-label={group.label}
      style={{ top }}
    >
      <span className="nav-flyout-title">{group.label}</span>
      {group.pages.map((p) => (
        <NavLink
          viewTransition
          key={p.to}
          to={p.to}
          end={p.end}
          className={({ isActive }) => `nav-flyout-link${isActive ? " active" : ""}`}
          onClick={() => onClose(false)}
        >
          <span className="ico">{p.icon}</span>
          <span>{p.label}</span>
        </NavLink>
      ))}
    </div>
  );
}

function SideNav({ quick, groups, rail, user }: { quick: NavPage[]; groups: NavGroup[]; rail: boolean; user: string }) {
  const { pathname } = useLocation();
  const current = groupOf(pathname);
  const [open, setOpen] = useState<OpenMap>(() => readOpenGroups(user));
  const [flyout, setFlyout] = useState<{ id: GroupId; anchor: HTMLElement } | null>(null);

  // O mapa guarda só as ESCOLHAS da pessoa; sem escolha, o grupo da tela atual fica aberto e os
  // outros fechados. Entrou numa tela de um grupo que ela tinha fechado (Ctrl+K, link de outra
  // página): a escolha antiga cai e o grupo abre — o menu sempre mostra onde a pessoa está.
  useEffect(() => {
    if (!current) return;
    setOpen((m) => {
      if (m[current] !== false) return m;
      const next = { ...m };
      delete next[current];
      writeOpenGroups(user, next);
      return next;
    });
  }, [current, user]);

  // Trocou de tela ou saiu do modo trilho: o painel lateral do grupo fecha.
  useEffect(() => setFlyout(null), [pathname, rail]);

  const isGroupOpen = (id: GroupId) => open[id] ?? id === current;

  const toggle = (id: GroupId) => {
    setOpen((m) => {
      const next = { ...m, [id]: !(m[id] ?? id === current) };
      writeOpenGroups(user, next);
      return next;
    });
  };

  const closeFlyout = useCallback((returnFocus: boolean) => {
    setFlyout((f) => {
      if (f && returnFocus) f.anchor.focus();
      return null;
    });
  }, []);

  const flyoutGroup = flyout ? groups.find((g) => g.id === flyout.id) : undefined;

  return (
    <nav className="nav" aria-label="Menu principal">
      <div className="nav-quick">
        {quick.map((p) => (
          <NavItem key={p.to} page={p} />
        ))}
      </div>

      {groups.map((g) => {
        // Grupo com uma página só (o barbeiro só vê "Meu perfil" em Configurar): vira link
        // direto — abrir um bloco para mostrar um item seria um clique a mais por nada.
        if (g.pages.length === 1) {
          return (
            <div className="nav-group single" key={g.id}>
              <NavItem page={g.pages[0]} />
            </div>
          );
        }
        const isOpen = isGroupOpen(g.id);
        const hasActive = current === g.id;
        const listId = `nav-group-${g.id}`;
        const flyoutId = `nav-flyout-${g.id}`;
        const flyoutOpen = flyout?.id === g.id;
        return (
          <div className={`nav-group${isOpen ? " open" : ""}${hasActive ? " has-active" : ""}`} key={g.id}>
            <button
              type="button"
              className="nav-group-toggle"
              aria-expanded={rail ? flyoutOpen : isOpen}
              aria-controls={rail ? (flyoutOpen ? flyoutId : undefined) : listId}
              title={rail ? g.label : undefined}
              data-tour={g.id === "configurar" ? "nav-settings" : undefined}
              onClick={(e) => {
                if (rail) {
                  const anchor = e.currentTarget;
                  setFlyout((f) => (f?.id === g.id ? null : { id: g.id, anchor }));
                } else {
                  toggle(g.id);
                }
              }}
            >
              <span className="ico">{g.icon}</span>
              <span className="lbl">{g.label}</span>
              {hasActive && !isOpen && (
                <span className="nav-dot">
                  <span className="sr-only">(tela atual)</span>
                </span>
              )}
              <span className="nav-chev" aria-hidden="true">
                <IconChevron />
              </span>
            </button>
            <div className="nav-sub" id={listId}>
              <div className="nav-sub-inner">
                {g.pages.map((p) => (
                  <NavItem key={p.to} page={p} sub />
                ))}
              </div>
            </div>
          </div>
        );
      })}

      {flyout && flyoutGroup && rail && (
        <GroupFlyout
          group={flyoutGroup}
          id={`nav-flyout-${flyoutGroup.id}`}
          anchor={flyout.anchor}
          onClose={closeFlyout}
        />
      )}
    </nav>
  );
}

/**
 * Navegação inferior do celular (≤760px): os mesmos atalhos do menu lateral, a 1 toque, e um
 * "Mais" que abre os grupos (Fidelização, Dinheiro, Configurar) numa folha — qualquer tela
 * fica a no máximo 2 toques (regra dos 2 cliques, UX_BLUEPRINT §5).
 */
function BottomNav({ quick, groups }: { quick: NavPage[]; groups: NavGroup[] }) {
  const { pathname } = useLocation();
  const [sheet, setSheet] = useState(false);
  const inGroup = groupOf(pathname) !== null;

  return (
    <>
      <nav className="bottom-nav" aria-label="Navegação do painel">
        {quick.map((p) => {
          return (
            <NavLink
              viewTransition
              key={p.to}
              to={p.to}
              end={p.end}
              className={() => `bn-item${pathMatches(p, pathname) ? " active" : ""}`}
              data-tour={tourId(p.to)}
            >
              <span className="bn-ico">{p.icon}</span>
              <span className="bn-lbl">{p.label}</span>
            </NavLink>
          );
        })}
        {groups.length > 0 && (
          <button
            type="button"
            className={`bn-item${inGroup ? " active" : ""}`}
            aria-current={inGroup ? "page" : undefined}
            aria-haspopup="dialog"
            aria-expanded={sheet}
            data-tour="nav-settings"
            onClick={() => setSheet(true)}
          >
            <span className="bn-ico">
              <IconMore />
            </span>
            <span className="bn-lbl">Mais</span>
          </button>
        )}
      </nav>
      {sheet && (
        <Drawer title="Mais" onClose={() => setSheet(false)} className="area-sheet">
          {groups.map((g) => (
            <section key={g.id} className="area-section" aria-labelledby={`sheet-${g.id}`}>
              <h4 className="area-section-title" id={`sheet-${g.id}`}>
                {g.label}
              </h4>
              <ul className="area-list">
                {g.pages.map((p) => (
                  <li key={p.to}>
                    <NavLink
                      viewTransition
                      to={p.to}
                      end={p.end}
                      className={({ isActive }) => `area-link${isActive ? " active" : ""}`}
                      onClick={() => setSheet(false)}
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
            </section>
          ))}
          <div className="area-install">
            <InstallAppButton className="btn btn-ghost" />
          </div>
        </Drawer>
      )}
    </>
  );
}

// Funções do plano (piloto sem clube/pacotes): busca uma vez por sessão e redesenha o menu.
let planFeaturesLoaded = false;
export function usePlanFeatures(enabled: boolean) {
  const [, bump] = useState(0);
  useEffect(() => {
    if (!enabled || planFeaturesLoaded) return;
    planFeaturesLoaded = true;
    api.get<{ plan: string | null; disabled: string[] }>("/api/admin/plan-features")
      .then((r) => { setDisabledFeatures(r.disabled ?? []); bump((n) => n + 1); })
      .catch(() => { planFeaturesLoaded = false; });
  }, [enabled]);
}

/** Página de uma função que o plano não libera (rota aberta direto pelo endereço). */
export function FeatureGate({ feature, children }: { feature: "CLUB" | "PACKAGES" | "LOYALTY"; children: ReactNode }) {
  usePlanFeatures(true);
  if (featureEnabled(feature)) return <>{children}</>;
  return (
    <Layout title="Não disponível no piloto">
      <div className="card" style={{ maxWidth: 560 }}>
        <h3 style={{ marginTop: 0 }}>Esta função não faz parte do piloto</h3>
        <p className="muted" style={{ marginBottom: 0 }}>Durante o teste grátis o clube de assinatura, os pacotes e o cartão
          fidelidade ficam desligados. Eles voltam quando a barbearia escolher um plano.</p>
      </div>
    </Layout>
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
  const [theme, setTheme] = useState<Theme>(currentTheme);
  usePlanFeatures(!!session);
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(COLLAPSED_KEY) === "true";
    } catch {
      return false;
    }
  });
  const narrow = useMedia(RAIL_QUERY);

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

  const toggleTheme = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    setTheme(next);
    applyTheme(next);
  };

  const initial = (session?.name || session?.email || "?").charAt(0).toUpperCase();

  // Menu filtrado pelo papel (lib/nav + lib/access): grupo sem página visível some inteiro.
  const quick = visibleQuick(session?.role);
  const groups = visibleGroups(session?.role);

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
        <SideNav
          key={session?.email ?? "anon"}
          quick={quick}
          groups={groups}
          rail={collapsed || narrow}
          user={session?.email ?? "anon"}
        />
        <div className="sidebar-foot">
          <InstallAppButton className="btn btn-ghost btn-sm install-btn" compact={collapsed || narrow} />
          <NavLink viewTransition to="/profile" className="user-chip" title="Meu perfil" data-tour="profile">
            <span className="avatar">{initial}</span>
            <div className="grow user-meta">
              <div className="user-name">{session?.name || "Admin"}</div>
              <div className="faint user-mail">{session?.email}</div>
            </div>
          </NavLink>
          {!collapsed && <NavLink viewTransition to="/sobre" className="app-version mono" title="Sobre o Razor e novidades">Razor v{__APP_VERSION__} · novidades</NavLink>}
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
        <div className={`content${flush ? " flush" : ""}`}>
          {/* Telas de tela cheia (agenda) ficam sem a faixa: ali cada pixel de altura é grade. */}
          {(session?.role === "OWNER" || session?.role === "RECEPTION") && !flush && (
            <HealthBanner canAct={session?.role === "OWNER"} />
          )}
          {session?.role === "OWNER" && !flush && <TrialBanner />}
          {children}
        </div>
      </div>

      <BottomNav quick={quick} groups={groups} />
      <StampLayer />
      {session?.role === "OWNER" && <FeedbackPrompt />}
    </div>
  );
}
