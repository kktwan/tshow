#!/usr/bin/env bash
# 삭제 요청 처리 도구. 제공처나 저작권자가 행사·이미지 삭제를 요청하면 서버에서 실행한다.
# 위치: /data/tshow/takedown.sh   실행 권한: chmod +x /data/tshow/takedown.sh
#
#   ./takedown.sh list                                   등록된 삭제 요청 목록
#   ./takedown.sh hide-event  <행사 UUID> "사유"          행사를 보이지 않게 한다 (그 행사를 이루는 모든 소스 레코드를 제외)
#   ./takedown.sh hide-image  <행사 UUID> "사유"          행사의 이미지만 쓰지 않는다
#   ./takedown.sh hide-record <KOPIS|CULTURE|TOURAPI> <소스의 id> "사유"   제공처가 자기 id 로 알려 준 항목을 제외
#   ./takedown.sh purge-source <KOPIS|CULTURE|TOURAPI>   그 소스의 데이터를 모두 지운다 (제공처가 사용 중단·전체 삭제를 요구할 때)
#
# 행사 UUID 는 화면 주소 /events/<UUID> 에 있다.
# 즉시 효과: 행사 숨김·이미지 삭제는 바로 화면(검색·상세·사이트맵)에 반영된다. 다음 병합부터는 삭제 요청 목록이 계속 적용하므로
#            수집이 매일 다시 돌아도 되살아나지 않는다. AI 추천 결과는 최대 30분 캐시된다. 검색 색인(Qdrant)은 다음 색인 때 정리된다.
set -euo pipefail

APP_DIR=/data/tshow
set -a; . "$APP_DIR/.env"; set +a
DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/([^/?]+).*#\1#')

psql_run() { docker exec -i -e PGPASSWORD="$DB_PASSWORD" infra-postgres psql -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"; }
sql_escape() { printf '%s' "${1//\'/\'\'}"; }
need_uuid() { [[ "$1" =~ ^[0-9a-fA-F-]{36}$ ]] || { echo "행사 UUID 형식이 아니에요: $1" >&2; exit 1; }; }
need_source() { [[ "$1" =~ ^(KOPIS|CULTURE|TOURAPI)$ ]] || { echo "소스는 KOPIS, CULTURE, TOURAPI 중 하나여야 해요: $1" >&2; exit 1; }; }

cmd=${1:-}
case "$cmd" in
  list)
    psql_run -c "select id, kind, source, source_id, reason, requested_at::timestamp(0) from takedown order by id desc limit 50"
    ;;
  hide-event)
    id=${2:?행사 UUID}; reason=${3:?사유}; need_uuid "$id"
    psql_run <<SQL
begin;
insert into takedown (kind, source, source_id, reason)
  select 'RECORD', r.source, r.source_id, '$(sql_escape "$reason")'
  from event_source es join source_record r on r.id = es.source_record_id
  where es.event_id = '$id'
  on conflict do nothing;
-- 바로 보이지 않게 한다 (다음 병합에서 이 행사는 소스 레코드가 모두 제외돼 사라진다)
update event set archived_at = now() where id = '$id';
commit;
select 'hidden' as result, count(*) as takedown_rows from takedown where reason = '$(sql_escape "$reason")';
SQL
    ;;
  hide-image)
    id=${2:?행사 UUID}; reason=${3:?사유}; need_uuid "$id"
    psql_run <<SQL
begin;
insert into takedown (kind, source, source_id, reason)
  select 'IMAGE', r.source, r.source_id, '$(sql_escape "$reason")'
  from event_source es join source_record r on r.id = es.source_record_id
  where es.event_id = '$id'
  on conflict do nothing;
update event set image_url = null, image_license = null where id = '$id';
commit;
select 'image removed' as result;
SQL
    ;;
  hide-record)
    source=${2:?소스}; sid=${3:?소스의 id}; reason=${4:?사유}; need_source "$source"
    psql_run <<SQL
begin;
insert into takedown (kind, source, source_id, reason)
  values ('RECORD', '$source', '$(sql_escape "$sid")', '$(sql_escape "$reason")') on conflict do nothing;
-- 이 레코드만으로 이루어진 행사는 바로 숨긴다. 다른 소스 레코드와 합쳐진 행사는 다음 병합 때 이 소스 내용만 빠진다
update event set archived_at = now()
  where id in (select es.event_id from event_source es join source_record r on r.id = es.source_record_id
               where r.source = '$source' and r.source_id = '$(sql_escape "$sid")')
    and (select count(*) from event_source x where x.event_id = event.id) = 1;
commit;
select 'recorded' as result;
SQL
    ;;
  purge-source)
    source=${2:?소스}; need_source "$source"
    echo "경고: $source 에서 온 소스 레코드를 모두 지웁니다. 그 소스에서만 온 행사는 보이지 않게 됩니다."
    echo "먼저 서버 /data/tshow/.env 의 TSHOW_INGEST_SOURCES 에서 $source 를 빼 두었는지 확인하세요 (안 그러면 내일 다시 수집됩니다)."
    read -r -p "계속하려면 소스 이름($source)을 그대로 입력: " answer
    [[ "$answer" == "$source" ]] || { echo "취소했어요."; exit 1; }
    psql_run <<SQL
begin;
delete from source_record where source = '$source';           -- 행사와의 연결(event_source)도 함께 지워진다
update event set archived_at = now()                           -- 연결이 모두 사라진 행사는 바로 보이지 않게 한다
  where archived_at is null and id not in (select event_id from event_source);
commit;
select count(*) as remaining_records from source_record where source = '$source';
SQL
    echo "완료. 소스 내용이 섞인 행사와 검색 색인은 다음 병합·색인(매일 03:00)에서 정리돼요."
    echo "DB 백업 파일($APP_DIR/backup)에도 데이터가 남아 있으니 필요하면 오래된 덤프를 지우세요."
    ;;
  *)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
