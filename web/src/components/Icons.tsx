// Ícones de linha do painel (brand book §7): outline 1.6px, cantos arredondados,
// no estilo Lucide — substituem os emojis genéricos da navegação.
import type { ReactNode } from "react";

function Icon({ children, size = 18 }: { children: ReactNode; size?: number }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

export const IconGrid = () => (
  <Icon>
    <rect x="3" y="3" width="7" height="7" rx="1.5" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" />
    <rect x="14" y="14" width="7" height="7" rx="1.5" />
  </Icon>
);

export const IconTag = () => (
  <Icon>
    <path d="M12.6 3.6 20.4 11.4a2 2 0 0 1 0 2.8l-6.2 6.2a2 2 0 0 1-2.8 0L3.6 12.6A2 2 0 0 1 3 11.2V5a2 2 0 0 1 2-2h6.2a2 2 0 0 1 1.4.6Z" />
    <circle cx="8" cy="8" r="1.4" />
  </Icon>
);

/** Produtos do balcão: caixa/embalagem. */
export const IconBox = () => (
  <Icon>
    <path d="M21 8v8a2 2 0 0 1-1 1.73l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.73l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8Z" />
    <path d="m3.3 7 8.7 5 8.7-5" />
    <path d="M12 22V12" />
  </Icon>
);

export const IconUser = () => (
  <Icon>
    <circle cx="12" cy="8" r="4" />
    <path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" />
  </Icon>
);

export const IconUsers = () => (
  <Icon>
    <circle cx="9" cy="8.5" r="3.5" />
    <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
    <path d="M16.5 4.6a3.5 3.5 0 0 1 0 7.8" />
    <path d="M17.5 14.1a6.5 6.5 0 0 1 4 5.9" />
  </Icon>
);

export const IconGem = () => (
  <Icon>
    <path d="M6.5 3h11L21 8.5 12 21 3 8.5 6.5 3Z" />
    <path d="M3 8.5h18" />
    <path d="M9 3l3 5.5L15 3" />
    <path d="M8 8.5 12 21l4-12.5" />
  </Icon>
);

export const IconPercent = () => (
  <Icon>
    <path d="M19 5 5 19" />
    <circle cx="7" cy="7" r="2.6" />
    <circle cx="17" cy="17" r="2.6" />
  </Icon>
);

export const IconCalendar = () => (
  <Icon>
    <rect x="3" y="5" width="18" height="16" rx="2.5" />
    <path d="M3 10h18M8 3v4M16 3v4" />
    <path d="M7.5 14h3M13.5 14h3M7.5 17.5h3" />
  </Icon>
);

export const IconSliders = () => (
  <Icon>
    <path d="M4 7h9M17 7h3" />
    <circle cx="15" cy="7" r="2" />
    <path d="M4 17h3M11 17h9" />
    <circle cx="9" cy="17" r="2" />
  </Icon>
);

export const IconCash = () => (
  <Icon>
    <rect x="2.5" y="6" width="19" height="12" rx="2" />
    <circle cx="12" cy="12" r="2.6" />
    <path d="M6 9.5v5M18 9.5v5" />
  </Icon>
);
