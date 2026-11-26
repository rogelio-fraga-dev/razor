#!/bin/bash
# Atualiza o Razor na EC2 do piloto. Chamado pelo GitHub Actions (via SSM) a cada push no master
# que passou nos testes. Uso: server-deploy.sh <bucket> <sha>
#
# Passos: baixa o pacote -> backup do banco -> monta a nova versao em releases/<sha> -> sobe
# -> confere a saude -> se falhar, volta para a versao anterior e sai com erro.
# O .env de producao fica em /opt/razor/shared/.env (nunca no pacote nem no git).
set -euo pipefail

BUCKET="$1"
SHA="$2"
BASE=/opt/razor
REL="$BASE/releases/$SHA"
COMPOSE=(docker compose -p razor -f docker-compose.yml -f docker-compose.prod.yml --profile bot)
log() { echo "[deploy $(date +%H:%M:%S)] $*"; }

mkdir -p "$BASE/releases" "$BASE/shared"
# Primeira vez: o .env que estava em /opt/razor/app vira o compartilhado.
if [ ! -f "$BASE/shared/.env" ] && [ -f "$BASE/app/.env" ]; then
  cp "$BASE/app/.env" "$BASE/shared/.env"
fi
chmod 600 "$BASE/shared/.env"

PREV=""
if [ -L "$BASE/current" ]; then PREV=$(readlink -f "$BASE/current"); elif [ -d "$BASE/app" ]; then PREV="$BASE/app"; fi
if [ "$PREV" = "$REL" ]; then log "versao $SHA ja esta no ar"; exit 0; fi

log "baixando o pacote $SHA"
rm -rf "$REL" && mkdir -p "$REL"
aws s3 cp "s3://$BUCKET/deploy/razor-$SHA.tgz" /tmp/razor-$SHA.tgz --only-show-errors
tar -xzf /tmp/razor-$SHA.tgz -C "$REL" && rm -f /tmp/razor-$SHA.tgz
ln -sf "$BASE/shared/.env" "$REL/.env"
echo "$SHA" > "$REL/.release"

echo "$BUCKET" > "$BASE/.bucket"
install -m 700 "$REL/deploy/backup.sh" "$BASE/backup.sh"
log "backup do banco antes de atualizar"
"$BASE/backup.sh" || { log "backup falhou: atualizacao cancelada"; exit 1; }

healthy() {
  for _ in $(seq 1 36); do
    b=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8081/actuator/health || true)
    w=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:3000/ || true)
    s=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8000/ || true)
    if [ "$b" = 200 ] && [ "$w" = 200 ] && [ "$s" = 200 ]; then return 0; fi
    sleep 5
  done
  log "saude: backend=$b painel=$w landing=$s"
  return 1
}

log "construindo e subindo a versao nova"
cd "$REL"
if "${COMPOSE[@]}" up -d --build --remove-orphans && healthy; then
  ln -sfn "$REL" "$BASE/current"
  log "OK: versao $SHA no ar"
  # Guarda as 3 ultimas versoes e limpa imagens velhas.
  ls -1dt "$BASE"/releases/*/ | tail -n +4 | xargs -r rm -rf
  docker image prune -f >/dev/null
  exit 0
fi

log "FALHOU: voltando para a versao anterior ($PREV)"
docker logs razor-backend-1 --tail 60 2>&1 | sed 's/^/  [backend] /' || true
if [ -n "$PREV" ] && [ -d "$PREV" ]; then
  cd "$PREV"
  [ -e "$PREV/.env" ] || ln -sf "$BASE/shared/.env" "$PREV/.env"
  "${COMPOSE[@]}" up -d --build --remove-orphans && healthy && log "versao anterior restaurada" || log "ATENCAO: a versao anterior tambem nao subiu"
fi
# O banco nao volta sozinho (migracoes so andam para frente); o backup de antes esta no S3.
exit 1
