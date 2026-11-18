// Barra de comandos (⌘K / Ctrl+K) — navegar e agir sem tirar a mão do teclado.
// É a assinatura de produto de ferramentas como Linear e Raycast, e aqui resolve
// um problema real: o dono chega em qualquer tela em 1 atalho.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { canAccess } from "../lib/access";
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

const COMMANDS: Cmd[] = [
  { id: "go-home", label: "Painel", hint: "visão do dia", group: "Ir para", to: "/", run: (n) => n("/") },
  { id: "go-agenda", label: "Agenda", hint: "calendário por barbeiro", group: "Ir para", to: "/agenda", run: (n) => n("/agenda") },
  { id: "go-customers", label: "Clientes", hint: "base e assinantes", group: "Ir para", to: "/customers", run: (n) => n("/customers") },
  { id: "go-services", label: "Serviços", hint: "preços e duração", group: "Ir para", to: "/services", run: (n) => n("/services") },
  { id: "go-comandas", label: "Comandas", hint: "conta do atendimento", group: "Ir para", to: "/comandas", run: (n) => n("/comandas") },
  { id: "go-products", label: "Produtos", hint: "catálogo do balcão", group: "Ir para", to: "/products", run: (n) => n("/products") },
  { id: "go-pros", label: "Profissionais", hint: "equipe e comissão", group: "Ir para", to: "/professionals", run: (n) => n("/professionals") },
  { id: "go-plans", label: "Planos", hint: "clube de assinatura", group: "Ir para", to: "/plans", run: (n) => n("/plans") },
  { id: "go-coupons", label: "Cupons", hint: "descontos e fidelidade", group: "Ir para", to: "/coupons", run: (n) => n("/coupons") },
  { id: "go-packages", label: "Pacotes", hint: "créditos pré-pagos", group: "Ir para", to: "/packages", run: (n) => n("/packages") },
  { id: "go-caixa", label: "Caixa", hint: "abrir, fechar e movimentos", group: "Ir para", to: "/caixa", run: (n) => n("/caixa") },
  { id: "go-expenses", label: "Despesas e resultado", hint: "contas a pagar e DRE", group: "Ir para", to: "/expenses", run: (n) => n("/expenses") },
  { id: "go-retention", label: "Ocupação e retenção", hint: "cadeiras vazias e quem sumiu", group: "Ir para", to: "/retention", run: (n) => n("/retention") },
  { id: "go-comm", label: "Comissões", hint: "fechamento por barbeiro", group: "Ir para", to: "/commissions", run: (n) => n("/commissions") },
  { id: "go-settlement", label: "Acerto de comissões", hint: "o que já foi pago", group: "Ir para", to: "/commissions/settlement", run: (n) => n("/commissions/settlement") },
  { id: "go-settings", label: "Configurações", hint: "bot, WhatsApp e equipe", group: "Ir para", to: "/settings", run: (n) => n("/settings") },
  { id: "go-profile", label: "Meu perfil", hint: "nome e senha", group: "Ir para", to: "/profile", run: (n) => n("/profile") },
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
  const allowed = useMemo(
    () => COMMANDS.filter((c) => !c.to || canAccess(c.to, session?.role)),
    [session?.role],
  );

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
    <div className="cmdk-overlay" onMouseDown={close} role="dialog" aria-label="Barra de comandos">
      <div className="cmdk" onMouseDown={(e) => e.stopPropagation()}>
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
