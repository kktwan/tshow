#!/usr/bin/env bash
# tshow 데이터베이스를 매일 덤프하고 오래된 것을 지운다.
# 위치: /data/tshow/pg-backup.sh (서버)   실행 권한: chmod +x /data/tshow/pg-backup.sh
# 크론 예 (매일 새벽 4시, 수집 파이프라인(3시)이 끝난 뒤):  0 4 * * * /data/tshow/pg-backup.sh >> /data/tshow/backup/backup.log 2>&1
#
# 복원 예:  gunzip -c /data/tshow/backup/tshow-YYYYmmdd.sql.gz | docker exec -i -e PGPASSWORD="$DB_PASSWORD" infra-postgres psql -U "$DB_USERNAME" -d tshow
# (event 와 벡터 색인은 source_record 에서 다시 만들 수 있지만, 다시 받으면 KOPIS 만 몇 시간이 걸리고 행사 id 가 바뀐다)
set -euo pipefail

APP_DIR=/data/tshow
BACKUP_DIR="$APP_DIR/backup"
KEEP_DAYS=7

mkdir -p "$BACKUP_DIR"
set -a; . "$APP_DIR/.env"; set +a
DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/([^/?]+).*#\1#')

OUT="$BACKUP_DIR/tshow-$(date +%Y%m%d).sql.gz"
docker exec -e PGPASSWORD="$DB_PASSWORD" infra-postgres pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --no-owner | gzip > "$OUT.tmp"
mv "$OUT.tmp" "$OUT"
chmod 600 "$OUT"

find "$BACKUP_DIR" -name 'tshow-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') 백업 완료: $OUT ($(du -h "$OUT" | cut -f1))"
