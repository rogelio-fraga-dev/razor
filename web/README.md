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

Em produção, defina `VITE_API_URL` se a API estiver em outro host; se o front e a API forem
servidos no mesmo domínio, deixe vazio.

## Estrutura

- `src/api/` — cliente HTTP (JWT, tratamento de 401) e tipos dos DTOs
- `src/auth/` — contexto de autenticação (sessão + login/logout)
- `src/pages/` — Login, Painel, Serviços, Profissionais, Comissões, Configurações
- `src/components/` — Layout (sidebar/topbar) e Modal
- `src/index.css` — design system (tokens, tema claro/escuro)
