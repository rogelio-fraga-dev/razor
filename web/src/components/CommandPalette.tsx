// Barra de comandos (⌘K / Ctrl+K) — navegar e agir sem tirar a mão do teclado.
// É a assinatura de produto de ferramentas como Linear e Raycast, e aqui resolve
// um problema real: o dono chega em qualquer tela em 1 atalho.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { canAccess } from "../lib/access";
import { reachablePages } from "../lib/nav";
import { applyTheme, currentTheme } from "../lib/theme";

interface Cmd {
  id: string;
  label: string;
  hint: string;
  group: "Ir para" | "Ações";
  /** Rota de destino, quando houver — usada para esconder do papel que não a alcança. */
  to?: string;
  run: (nav: ReturnType<typeof useNavigate>) => void;
}

// "Ir para" sai de lib/nav (a mesma fonte do menu e da barra inferior): toda tela nova entra
// no Ctrl+K sem ninguém lembrar de cadastrar aqui. Abaixo, só as AÇÕES.
const ACTIONS: Cmd[] = [
  {
    id: "act-theme",
    label: "Alternar tema claro/escuro",
    hint: "aparência",
    group: "Ações",
    run: () => applyTheme(currentTheme() === "dark" ? "light" : "dark"),
  },
  { id: "act-wpp", label: "Conectar WhatsApp", hint: "QR code", group: "Ações", to: "/settings", run: (n) => n("/settings") },
  { id: "act-new-service", label: "Novo serviço", hint: "catálogo", group: "Ações", to: "/services", run: (n) => n("/services") },
  { id: "act-new-product", label: "Novo produto", hint: "balcão", group: "Ações", to: "/products", run: (n) => n("/products") },
];

function norm(s: string) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

export function CommandPalette() {
  const nav = useNavigate();
  const { session } = useAuth();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [idx, setIdx] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // Mesmo critério do menu lateral: comando que leva a uma tela fora do papel não aparece.
  // Sem isso a barra viraria o caminho aberto para telas que o menu já esconde.
  const allowed = useMemo(() => {
    const pages: Cmd[] = reachablePages(session?.role).map((p) => ({
      id: `go${p.to.replace(/\W+/g, "-")}`,
      label: p.label,
      hint: p.hint,
      group: "Ir para",
      to: p.to,
      run: (n) => n(p.to),
    }));
    return [...pages, ...ACTIONS.filter((c) => !c.to || canAccess(c.to, session?.role))];
  }, [session?.role]);

  const results = useMemo(() => {
    const term = norm(q.trim());
    if (!term) return allowed;
    return allowed.filter((c) => norm(c.label).includes(term) || norm(c.hint).includes(term));
  }, [q, allowed]);

  const close = useCallback(() => {
    setOpen(false);
    setQ("");
    setIdx(0);
  }, []);

  // Atalho global
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      }
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 20);
  }, [open]);

  useEffect(() => setIdx(0), [q]);

  if (!open) return null;

  const runAt = (i: number) => {
    const cmd = results[i];
    if (!cmd) return;
    close();
    cmd.run(nav);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setIdx((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      runAt(idx);
    }
  };

  let lastGroup = "";

  return (
    <div
      className="cmdk-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="cmdk" role="dialog" aria-modal="true" aria-label="Barra de comandos">
        <div className="cmdk-input">
          <span className="cmdk-ico" aria-hidden="true">⌘</span>
          {/* Padrão de combobox do WAI-ARIA.
              As setas movem só um índice de estado e o foco nunca sai do input — sem
              aria-activedescendant o leitor de tela não anuncia nada enquanto a pessoa navega,
              e ela aperta Enter às cegas. */}
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Buscar telas e ações…"
            aria-label="Buscar comandos"
            role="combobox"
            aria-expanded="true"
            aria-controls="cmdk-listbox"
            aria-autocomplete="list"
            aria-activedescendant={results[idx] ? `cmdk-opt-${results[idx].id}` : undefined}
          />
          <kbd>esc</kbd>
        </div>
        {/* Contagem anunciada sem mover o foco: quem digita precisa saber se sobrou algo. */}
        <span className="sr-only" role="status" aria-live="polite">
          {results.length} resultado(s){q ? ` para ${q}` : ""}
        </span>
        <div className="cmdk-list" id="cmdk-listbox" role="listbox">
          {results.length === 0 && <div className="cmdk-empty">Nada encontrado para “{q}”.</div>}
          {results.map((c, i) => {
            const head = c.group !== lastGroup ? ((lastGroup = c.group), c.group) : null;
            return (
              <div key={c.id}>
                {head && <div className="cmdk-group mono">{head}</div>}
                <button
                  id={`cmdk-opt-${c.id}`}
                  role="option"
                  aria-selected={i === idx}
                  className={`cmdk-item ${i === idx ? "active" : ""}`}
                  onMouseEnter={() => setIdx(i)}
                  onClick={() => runAt(i)}
                >
                  <span className="cmdk-label">{c.label}</span>
                  <span className="cmdk-hint">{c.hint}</span>
                  <span className="cmdk-enter" aria-hidden="true">↵</span>
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
