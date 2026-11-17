// Abas simples e acessíveis (UX_BLUEPRINT §4.7): mata o "corredor de cards" de Configurações.
export interface TabDef {
  id: string;
  label: string;
}

/** Ids do par aba/painel — o painel usa `aria-labelledby` com o id da aba. */
export const tabId = (prefix: string, id: string) => `${prefix}-tab-${id}`;
export const panelId = (prefix: string, id: string) => `${prefix}-panel-${id}`;

export function Tabs({
  tabs,
  active,
  onChange,
  idPrefix = "tabs",
  label = "Seções",
}: {
  tabs: TabDef[];
  active: string;
  onChange: (id: string) => void;
  /** Prefixo dos ids de aba/painel (`aria-controls`). */
  idPrefix?: string;
  label?: string;
}) {
  const move = (dir: 1 | -1) => {
    const i = tabs.findIndex((t) => t.id === active);
    const next = tabs[(i + dir + tabs.length) % tabs.length];
    onChange(next.id);
    // O foco acompanha a seta (padrão WAI-ARIA de tabs): sem isso a aba muda e o foco fica
    // numa aba que agora tem tabIndex -1.
    requestAnimationFrame(() => document.getElementById(tabId(idPrefix, next.id))?.focus());
  };

  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          id={tabId(idPrefix, t.id)}
          aria-controls={panelId(idPrefix, t.id)}
          aria-selected={t.id === active}
          tabIndex={t.id === active ? 0 : -1}
          className={`tab ${t.id === active ? "active" : ""}`}
          onClick={() => onChange(t.id)}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight") move(1);
            if (e.key === "ArrowLeft") move(-1);
          }}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
