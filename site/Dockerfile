# Landing institucional do Razor.
#
# Nao tem build: sao HTML, CSS, JS e SVG escritos a mao. A imagem e so o nginx com os
# arquivos dentro — por isso um estagio unico, ao contrario do painel (web/Dockerfile),
# que precisa compilar o Vite antes.

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/razor-security-headers.conf

# Gera o config.js a partir de RAZOR_PANEL_URL no boot. A imagem do nginx executa
# /docker-entrypoint.d/*.sh antes de subir o servidor.
COPY docker-entrypoint.d/10-panel-url.sh /docker-entrypoint.d/10-panel-url.sh
RUN chmod +x /docker-entrypoint.d/10-panel-url.sh

COPY index.html styles.css motion.css script.js config.js favicon.svg logo.svg og-image.png /usr/share/nginx/html/
# Paginas legais (RASCUNHO ate a revisao do advogado; ver docs/MVP-PILOTO.md).
COPY termos.html privacidade.html cancelamento.html legal.css legal.js /usr/share/nginx/html/

EXPOSE 80
