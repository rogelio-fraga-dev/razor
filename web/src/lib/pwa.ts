// PWA: registro do service worker e o convite "Instalar app".
import { useEffect, useState } from "react";

// Registro do service worker. Só em build de produção: no `npm run dev` um SW guardando a
// casca do app brigaria com o hot reload do Vite e serviria arquivos velhos ao desenvolvedor.
// O sw.js nunca toca em /api (ver public/sw.js) — dado do painel é sempre ao vivo.
export function registerServiceWorker(): void {
  listenForInstallPrompt();
  if (!import.meta.env.PROD) return;
  if (!("serviceWorker" in navigator)) return;
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => {
      /* sem SW o painel funciona igual, só não abre offline */
    });
  });
}

// ---------------------------------------------------------------------------------------------
// "Instalar app"
//
// Chrome/Edge/Android disparam `beforeinstallprompt` quando o painel é instalável (manifest +
// SW + HTTPS ou localhost). O evento chega UMA vez e cedo — muitas vezes antes de o React montar
// — então é guardado aqui, no carregamento do módulo, e as telas só leem o que ficou guardado.
// O iPhone não tem esse evento: lá a instalação é manual (Compartilhar → Adicionar à Tela de
// Início), e o botão mostra o passo a passo.

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

let deferred: BeforeInstallPromptEvent | null = null;
let installed = false;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());
let listening = false;

function listenForInstallPrompt() {
  if (listening || typeof window === "undefined") return;
  listening = true;
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault(); // sem a mini-barra do Chrome: o convite é o nosso botão
    deferred = e as BeforeInstallPromptEvent;
    notify();
  });
  window.addEventListener("appinstalled", () => {
    installed = true;
    deferred = null;
    notify();
  });
}

/** Já está aberto como app (ícone na tela inicial)? */
export function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  const nav = navigator as Navigator & { standalone?: boolean };
  return window.matchMedia?.("(display-mode: standalone)").matches === true || nav.standalone === true;
}

/** iPhone/iPad (inclui o iPad que se apresenta como Mac). */
export function isIos(): boolean {
  if (typeof navigator === "undefined") return false;
  const ua = navigator.userAgent;
  return /iphone|ipad|ipod/i.test(ua) || (ua.includes("Macintosh") && navigator.maxTouchPoints > 1);
}

export interface InstallState {
  /** O navegador ofereceu a instalação: `prompt()` abre o diálogo nativo. */
  canPrompt: boolean;
  /** iPhone/iPad fora do app instalado: mostrar as instruções manuais. */
  showIosHelp: boolean;
  /** Já instalado / aberto como app: não oferecer nada. */
  installed: boolean;
  prompt: () => Promise<boolean>;
}

export function useInstallPrompt(): InstallState {
  const [, force] = useState(0);
  useEffect(() => {
    const l = () => force((n) => n + 1);
    listeners.add(l);
    return () => {
      listeners.delete(l);
    };
  }, []);
  const standalone = installed || isStandalone();
  return {
    canPrompt: !standalone && deferred !== null,
    showIosHelp: !standalone && deferred === null && isIos(),
    installed: standalone,
    prompt: async () => {
      const ev = deferred;
      if (!ev) return false;
      deferred = null; // o evento só pode ser usado uma vez
      notify();
      await ev.prompt();
      const choice = await ev.userChoice.catch(() => ({ outcome: "dismissed" as const }));
      return choice.outcome === "accepted";
    },
  };
}
