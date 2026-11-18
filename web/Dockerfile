# Painel do Razor (React + Vite + TypeScript).
#
# O Vite gera arquivos estaticos; quem serve e o nginx, que tambem encaminha /api para o
# backend — o mesmo papel que o proxy do vite faz em `npm run dev`. Assim o painel e a API
# ficam na mesma origem e nao existe CORS no meio.

# ---------- estagio 1: compila ----------
FROM node:20-alpine AS build
WORKDIR /build

# package*.json primeiro: o `npm ci` so refaz quando as dependencias mudam de verdade.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Endereco da landing, usado pelo link "voltar ao site" na tela de login. O Vite resolve
# import.meta.env em tempo de BUILD, entao precisa entrar aqui e nao no `environment` do
# compose — variavel de runtime nao alcançaria o bundle ja compilado.
ARG VITE_SITE_URL=""
ENV VITE_SITE_URL=$VITE_SITE_URL

# `npm run build` = tsc -b && vite build. Erro de tipo derruba o build aqui, de proposito.
RUN npm run build

# ---------- estagio 2: serve ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
# Cabecalhos de seguranca (CSP etc.), incluidos em cada location do nginx.conf.
COPY security-headers.conf /etc/nginx/snippets/razor-security-headers.conf
# Variante para a previa do editor do site (/site-previa), enquadravel so pela propria origem.
COPY security-headers-frameable.conf /etc/nginx/snippets/razor-security-headers-frameable.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
