# Razor · Painel Web

SPA (React + TypeScript + Vite) do painel de gestão da barbearia. Consome a API REST
`/api/admin/**` do backend Spring (autenticação por JWT).

## Rodar em desenvolvimento

```bash
cd web
npm install
npm run dev
```

O Vite sobe em `http://localhost:5173` e faz **proxy** de `/api` para o backend
(`http://localhost:8081` por padrão — ajuste com `VITE_API_PROXY`). Suba o backend Spring
antes e faça login com um usuário `tenant_users` (crie via `/api/onboarding`).

## Build de produção

```bash
npm run build      # gera dist/ (estático)
npm run preview    # serve o build localmente
```

Painel e API precisam ficar na **mesma origem** (em dev o proxy do Vite, no Docker o nginx
encaminham `/api`): a sessão é um cookie `HttpOnly; SameSite=Strict` e o token CSRF
(`XSRF-TOKEN`) só é legível pelo JS da própria origem. Deixe `VITE_API_URL` vazio.

## Estrutura

- `src/api/` — cliente HTTP (cookie de sessão + header CSRF, tratamento de 401) e tipos dos DTOs
- `src/auth/` — contexto de autenticação (sessão + login/logout)
- `src/pages/` — Login, Painel, Serviços, Profissionais, Comissões, Configurações
- `src/components/` — Layout (sidebar/topbar) e Modal
- `src/index.css` — design system (tokens, tema claro/escuro)
