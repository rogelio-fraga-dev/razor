#!/bin/bash
# Backup do banco do Razor (inclui a sessao do WhatsApp da Evolution) para o S3.
# Roda todo dia as 03:00 (/etc/cron.d/razor-backup) e antes de cada atualizacao.
# O bucket apaga backups com mais de 30 dias sozinho.
set -euo pipefail
ENV=/opt/razor/shared/.env
[ -f "$ENV" ] || ENV=/opt/razor/app/.env
B=$(cat /opt/razor/.bucket 2>/dev/null || echo razor-backup-119d7020)
F=razor-$(date +%Y%m%d-%H%M%S).dump
U=$(grep ^POSTGRES_USER= "$ENV" | cut -d= -f2)
docker exec razor-postgres-1 pg_dump -U "$U" -d barberbot_db -Fc > /tmp/$F
aws s3 cp /tmp/$F s3://$B/backups/$F --only-show-errors
rm -f /tmp/$F
echo "$(date -Is) backup ok: $F" >> /var/log/razor-backup.log
