// Abas simples e acessíveis (UX_BLUEPRINT §4.7): mata o "corredor de cards" de Configurações.
export interface TabDef {
  id: string;
  label: string;
}

export function Tabs({
  tabs,
  active,
  onChange,
}: {
  tabs: TabDef[];
  active: string;
  onChange: (id: string) => void;
}) {
  const move = (dir: 1 | -1) => {
    const i = tabs.findIndex((t) => t.id === active);
    const next = tabs[(i + dir + tabs.length) % tabs.length];
    onChange(next.id);
  };

  return (
    <div className="tabs" role="tablist" aria-label="Seções">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
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
