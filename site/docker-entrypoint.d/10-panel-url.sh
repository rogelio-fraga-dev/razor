#!/bin/sh
# Escreve o config.js da landing a partir do ambiente, antes do nginx subir.
#
# A imagem oficial do nginx executa tudo que esta em /docker-entrypoint.d/ no boot, entao
# este script roda uma vez por container. O endereco do painel muda por ambiente (Docker
# local, Vite em dev, dominio proprio em producao) e nao pode ficar cravado no estatico —
# era exatamente esse o problema do `localhost` que estava dentro do script.js.
set -eu

: "${RAZOR_PANEL_URL:=}"

TARGET=/usr/share/nginx/html/config.js

if [ -z "$RAZOR_PANEL_URL" ]; then
  echo "[razor-site] RAZOR_PANEL_URL vazia — a landing vai usar o fallback por hostname."
  exit 0
fi

# Aspas simples no JS e o valor com as aspas simples escapadas: o endereco vem do ambiente,
# entao trata-se como dado, nunca como codigo a ser concatenado sem cuidado.
ESCAPED=$(printf '%s' "$RAZOR_PANEL_URL" | sed "s/'/\\\\'/g")

cat > "$TARGET" <<EOF
// Gerado no boot do container a partir de RAZOR_PANEL_URL. Nao editar aqui.
window.RAZOR_CONFIG = {
  panelUrl: '${ESCAPED}',
};
EOF

echo "[razor-site] painel apontado para ${RAZOR_PANEL_URL}"
