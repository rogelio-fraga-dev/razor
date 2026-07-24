// Barra de comandos (⌘K / Ctrl+K) — navegar e agir sem tirar a mão do teclado.
// É a assinatura de produto de ferramentas como Linear e Raycast, e aqui resolve
// um problema real: o dono chega em qualquer tela em 1 atalho.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { applyTheme, currentTheme } from "../lib/theme";

interface Cmd {
  id: string;
  label: string;
  hint: string;
  group: "Ir para" | "Ações";
  run: (nav: ReturnType<typeof useNavigate>) => void;
}

const COMMANDS: Cmd[] = [
  { id: "go-home", label: "Painel", hint: "visão do dia", group: "Ir para", run: (n) => n("/") },
  { id: "go-agenda", label: "Agenda", hint: "calendário por barbeiro", group: "Ir para", run: (n) => n("/agenda") },
  { id: "go-customers", label: "Clientes", hint: "base e assinantes", group: "Ir para", run: (n) => n("/customers") },
  { id: "go-services", label: "Serviços", hint: "preços e duração", group: "Ir para", run: (n) => n("/services") },
  { id: "go-pros", label: "Profissionais", hint: "equipe e comissão", group: "Ir para", run: (n) => n("/professionals") },
  { id: "go-plans", label: "Planos", hint: "clube de assinatura", group: "Ir para", run: (n) => n("/plans") },
  { id: "go-caixa", label: "Caixa", hint: "abrir, fechar e movimentos", group: "Ir para", run: (n) => n("/caixa") },
  { id: "go-comm", label: "Comissões", hint: "fechamento por barbeiro", group: "Ir para", run: (n) => n("/commissions") },
  { id: "go-settings", label: "Configurações", hint: "bot, WhatsApp e equipe", group: "Ir para", run: (n) => n("/settings") },
  { id: "go-profile", label: "Meu perfil", hint: "nome e senha", group: "Ir para", run: (n) => n("/profile") },
  {
    id: "act-theme",
    label: "Alternar tema claro/escuro",
    hint: "aparência",
    group: "Ações",
    run: () => applyTheme(currentTheme() === "dark" ? "light" : "dark"),
  },
  { id: "act-wpp", label: "Conectar WhatsApp", hint: "QR code", group: "Ações", run: (n) => n("/settings") },
  { id: "act-new-service", label: "Novo serviço", hint: "catálogo", group: "Ações", run: (n) => n("/services") },
];

function norm(s: string) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

export function CommandPalette() {
  const nav = useNavigate();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [idx, setIdx] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const results = useMemo(() => {
    const term = norm(q.trim());
    if (!term) return COMMANDS;
    return COMMANDS.filter((c) => norm(c.label).includes(term) || norm(c.hint).includes(term));
  }, [q]);

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
    <div className="cmdk-overlay" onMouseDown={close} role="dialog" aria-label="Barra de comandos">
      <div className="cmdk" onMouseDown={(e) => e.stopPropagation()}>
        <div className="cmdk-input">
          <span className="cmdk-ico" aria-hidden="true">⌘</span>
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Buscar telas e ações…"
            aria-label="Buscar comandos"
          />
          <kbd>esc</kbd>
        </div>
        <div className="cmdk-list">
          {results.length === 0 && <div className="cmdk-empty">Nada encontrado para “{q}”.</div>}
          {results.map((c, i) => {
            const head = c.group !== lastGroup ? ((lastGroup = c.group), c.group) : null;
            return (
              <div key={c.id}>
                {head && <div className="cmdk-group mono">{head}</div>}
                <button
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
