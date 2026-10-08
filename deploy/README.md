# 배포 가이드 (Oracle Cloud A1 + Jenkins Blue-Green)

공용 인프라(`infra-nginx`, `infra-certbot`, `infra-jenkins`, `infra-postgres`, `infra-qdrant`, 네트워크 `infra-net`)는 이미 있고, tshow는 **자기 앱 컨테이너(`tshow-blue`/`tshow-green`), 자기 데이터베이스, 자기 Nginx 설정, 자기 도메인**만 추가한다.

> 에이전트는 코드와 배포 파일을 만드는 데까지만 한다. 아래 서버 작업, Git push, Jenkins 빌드는 사용자가 직접 한다.

## 구성 파일

| 파일 | 서버에서의 위치 | 역할 |
|---|---|---|
| `Jenkinsfile` | (Jenkins 파이프라인이 읽음) | 체크아웃 → 이미지 빌드 → 새 슬롯 기동 → 헬스체크 → Nginx 전환 → 구 슬롯 중지. 롤백 파라미터 지원 |
| `Dockerfile` | | 멀티스테이지 빌드 (JDK 21 → JRE 21), `/actuator/health` 헬스체크 |
| `deploy/docker-compose.app.yml` | `/data/tshow/docker-compose.app.yml` | `tshow-blue` / `tshow-green` 슬롯 (Jenkins가 복사해 준다) |
| `deploy/nginx/tshow.conf` | `/data/infra/nginx/conf.d/tshow.conf` | `tshow.duckdns.org` 가상 호스트 (80 → 443, 활성 슬롯으로 프록시). **검색 호출 제한 포함**: 검색어(`q`)가 있는 요청은 IP당 분당 30번(한꺼번에 15번까지), 전체 요청은 초당 20번. 넘으면 429 와 안내 문구. 임베딩 호출 비용과 남용을 막는다 |
| `deploy/nginx/tshow-url.inc` | `/data/infra/nginx/conf.d/tshow-url.inc` | 활성 슬롯 이름 (Jenkins가 바꾸고 `nginx -s reload`) |
| `deploy/.env.example` | `/data/tshow/.env` | 서버 환경변수 (DB 비밀번호, API 키). **서버에만 둔다** |

## 현재 서버 상태 (2026-10-08 기준)

에이전트가 서버에 아래까지 세팅해 두었다. **남은 것은 Jenkins 작업 만들기와 첫 배포뿐이다.**

| 항목 | 상태 |
|---|---|
| DuckDNS `tshow.duckdns.org` | 서버 IP로 연결됨 (사용자가 생성) |
| 공용 Postgres의 `tshow` 사용자·데이터베이스 | 생성됨 (비밀번호는 `/data/tshow/.env`에만 있음) |
| `/data/tshow/.env` | 생성됨 (권한 600: DB 계정, KOPIS·TourAPI·문화정보원 키, `TSHOW_INGEST_ON_STARTUP=false`) |
| `/data/tshow/docker-compose.app.yml` | 배치됨 (Jenkins가 배포 때마다 다시 복사) |
| Nginx `tshow.conf` (80+443), `tshow-url.inc` | 적용됨. 앱 배포 전이라 `https://tshow.duckdns.org`은 502 |
| Let's Encrypt 인증서 | 발급됨, 만료 2027-01-06 (`infra-certbot`이 자동 갱신) |
| Jenkins `tshow` 작업 | **아직 없음** (아래 5번, 사용자가 만든다) |
| 앱 컨테이너(`tshow-blue/green`) | **아직 없음** (첫 Jenkins 빌드가 만든다) |

## 서버에서 처음 한 번 할 일

1. **도메인**: DuckDNS에 `tshow` 서브도메인을 만들고 서버 IP를 가리키게 한다.

2. **데이터베이스**: 공용 Postgres에 tshow 전용 사용자와 DB를 만든다 (비밀번호는 직접 정한다).
   ```bash
   docker exec -it infra-postgres psql -U <공용_Postgres_관리자_계정> -d postgres \
     -c "CREATE USER tshow WITH PASSWORD '여기에_비밀번호';" \
     -c "CREATE DATABASE tshow OWNER tshow;"
   ```
   테이블은 앱이 처음 뜰 때 Flyway가 `db/migration`의 SQL로 만든다.

3. **앱 폴더와 환경변수**
   ```bash
   mkdir -p /data/tshow
   # deploy/.env.example 내용을 /data/tshow/.env 로 만들고 값을 채운다 (DB_PASSWORD, KOPIS_API_KEY, TOURAPI_API_KEY, CULTURE_API_KEY)
   chmod 600 /data/tshow/.env
   ```

4. **Nginx 설정** (인증서가 있어야 443 블록이 동작하므로 두 단계로 한다)
   1. `tshow-url.inc`를 `/data/infra/nginx/conf.d/`에 복사한다 (443 블록이 이 파일을 include 한다).
   2. `tshow.conf`에서 **80 블록만** 먼저 `/data/infra/nginx/conf.d/tshow.conf`로 둔다 → `docker exec infra-nginx nginx -t && docker exec infra-nginx nginx -s reload`
   3. 인증서 발급 (이메일은 본인 것):
      ```bash
      docker exec infra-certbot certbot certonly --webroot -w /var/www/certbot \
        -d tshow.duckdns.org --agree-tos -m 본인@이메일 --non-interactive
      ```
   4. `tshow.conf` 전체(80 + 443)로 교체하고 다시 `nginx -t` → `nginx -s reload`. 갱신은 `infra-certbot`이 알아서 한다.

5. **Jenkins 작업 만들기**: 새 항목 → 이름 `tshow` → Pipeline → "Pipeline script from SCM" → Git, 저장소 `https://github.com/kktwan/tshow.git`, 브랜치 `main`, Script Path `Jenkinsfile`. (저장소가 비공개면 Jenkins에 GitHub 자격 증명을 추가해 연결한다.)

## 배포

1. 코드를 `main`(또는 운영 브랜치)에 푸시한다.
2. Jenkins `tshow` 작업에서 `Build with Parameters` → 브랜치 선택 → 빌드.
3. 빌드가 새 슬롯을 띄우고 `/actuator/health`가 통과하면 Nginx를 전환하고 이전 슬롯을 멈춘다.
4. 확인: `https://tshow.duckdns.org/actuator/health` → `{"status":"UP"}`
5. 롤백: 같은 작업에서 `ROLLBACK`을 체크하고 빌드하면 이미지를 새로 만들지 않고 직전 `prod-*` 이미지로 되돌린다.

## 처음 데이터 수집 (운영에서 전체를 한 번 받는다)

로컬에는 개발용으로 일부만 받아 두었고, **전체 수집은 운영 서버에서 처음 한 번** 돌린다.

1. `/data/tshow/.env`에 `TSHOW_INGEST_ON_STARTUP=true`를 넣고 앱을 재시작한다 (`docker restart tshow-blue` 등 현재 슬롯).
2. 앱 시작 직후 오늘부터 6개월(`tshow.ingest.horizon-months`)을 소스별로 수집한다. **KOPIS는 호출 간격을 둬서 오래 걸린다** (실측: 6개월치 3,135건이 약 1시간, 분당 50건 안팎. 문화정보원 약 4분, TourAPI 약 3분). 진행은 로그로 본다.
   ```bash
   docker logs -f tshow-blue 2>&1 | grep -i "수집"
   ```
3. 끝나면 `.env`에서 `TSHOW_INGEST_ON_STARTUP`을 지우거나 `false`로 되돌리고 재시작한다. 이후에는 매일 새벽 3시(cron)에 증분 수집한다 (최근 24시간 안에 받은 항목은 상세를 다시 조회하지 않는다).
4. **수집 중에는 배포하지 않는다.** 배포하면 이전 슬롯이 멈춰 수집이 중단된다. 중단돼도 이미 받은 것은 저장돼 있고 다시 실행하면 이어서 진행한다.
5. 결과 확인 (실행 기록과 "매핑 안 된 값" 리포트):
   ```bash
   docker exec infra-postgres psql -U tshow -d tshow -c \
     "select id, source, status, fetched_count, failed_count, unmapped from ingest_run order by id desc limit 5"
   ```
   `unmapped`에 값이 나오면 `src/main/resources/taxonomy/`의 표(분류·지역 별칭)를 고친다.

## 알아 둘 점

- 수집기는 슬롯이 하나만 떠 있을 때(정상 운영) 안전하다. 두 슬롯이 동시에 떠 있는 짧은 전환 구간에는 정기 수집 시각(새벽 3시)을 피한다.
- 공용 Qdrant는 다른 서비스와 함께 쓴다. tshow의 벡터 컬렉션은 이름을 따로 정해서 만든다 (색인 단계에서 추가).
- 메모리: tshow 컨테이너는 `mem_limit 1g`, JVM `-Xmx768m`으로 시작한다. 벡터 색인·AI를 붙이면 늘린다.

## 운영 첫 수집 결과 (2026-10-08)

| 소스 | 건수 | 소요 |
|---|---|---|
| 문화정보원 | 939 | 4분 |
| KOPIS (6개월) | 3,135 | 약 1시간 |
| TourAPI | 264 | 3분 |

- 병합 후 **행사 3,873건** (여러 소스 병합 481건). 실패 0건.
- 매핑 안 된 값은 KOPIS의 인천 옛 구 이름(`동구`, `서구`, `중구` 28건)뿐이었다. 2026년 개편으로 구가 바뀌었는데 소스는 옛 주소를 준다. `서구`는 `서해구`/`검단구` 둘로 갈라져 한 구로 정할 수 없어서 별칭을 추가하지 않고 시도 단위로 둔다 (필요하면 `region-aliases.yml`의 `sigungu` 별칭에 추가).
- 수집이 끝났으면 `.env`의 `TSHOW_INGEST_ON_STARTUP`을 `false`로 되돌린다 (그대로 두면 재시작 때마다 수집이 다시 시작된다).

## 호출 제한 (nginx)

`deploy/nginx/tshow.conf` 의 `limit_req_zone` 두 개(`tshow_search`, `tshow_general`)가 한다. 값은 같은 파일에서 고친다.
- 적용 방법: 파일을 서버 `/data/infra/nginx/conf.d/tshow.conf` 로 두고 `docker exec infra-nginx nginx -t && docker exec infra-nginx nginx -s reload`.
- 2026-10-08 적용함. 적용 전 파일은 서버 `/data/tshow/tshow.conf.bak-before-limit` 에 있다 (되돌릴 때 사용).
- 확인: `curl` 로 `/api/search?q=...` 를 연달아 부르면 17번째쯤부터 429 가 나온다. 제한에 걸렸을 때는 nginx 로그에 `limiting requests` 가 남는다.
- **AI 추천 제한**: `/recommend`, `/api/recommend` 는 IP당 분당 6번(한꺼번에 3번까지)이다 (`tshow_ai`). 앱에도 사람별·전체 하루 한도가 있다 (`tshow.recommend.daily-limit-per-client`, `daily-limit-total`). 이 설정은 아직 서버에 적용하지 않았다 — 위 적용 방법대로 파일을 두고 `nginx -t` 뒤 reload 한다.

## 데이터베이스 백업

서버에는 백업이 아직 없다. `deploy/backup/pg-backup.sh` 를 `/data/tshow/pg-backup.sh` 로 두고 실행 권한(`chmod +x`)을 준 뒤 크론에 등록한다 (매일 새벽 4시, 7일 보관). 복원 방법은 스크립트 위쪽 주석에 있다. event 와 벡터 색인은 `source_record` 에서 다시 만들 수 있지만, 다시 받으면 KOPIS만 몇 시간이 걸리고 행사 id 가 바뀐다.

## 화면 하단 연락처

`TSHOW_CONTACT_EMAIL` 에 정정·삭제 요청을 받을 이메일을 넣으면 화면 하단과 `/about` 에 보인다. 비워 두면 보이지 않는다. 서버 `.env` 에 넣은 뒤 컨테이너를 다시 만들어야 적용된다.

## AI 추천 키 (Gemini)

서버 `/data/tshow/.env` 에 `GEMINI_API_KEY` 를 넣고 컨테이너를 다시 만들어야(`docker compose up -d --force-recreate`) 적용된다 (`docker restart` 는 env 를 다시 읽지 않는다). 키가 없으면 앱은 정상 동작하고 AI 추천 버튼만 보이지 않는다. 모델은 `GEMINI_MODEL`(기본 `gemini-3.1-flash-lite-preview`), 생각 시간은 `GEMINI_THINKING_LEVEL`(기본 `MINIMAL`) — `thinking-level: MINIMAL` 과 `thinking-budget: 0` 을 함께 쓰면 400 오류가 난다.

## DB 마이그레이션 배포 규칙

2026-10-08 배포에서 마이그레이션 V5(`ALTER TABLE`)가 DBeaver 의 닫히지 않은 트랜잭션 락에 막혀 멈췄고, 대기 중인 `ALTER` 가 뒤따르는 조회까지 막아 **첫 화면과 검색이 약 10분간 응답하지 않았다**. 그래서:

- **배포 전에 DB 도구(DBeaver 등)의 열린 트랜잭션을 닫는다** (Commit/Rollback, 또는 Auto-commit 으로 연결). 확인 쿼리:
  `select pid, application_name, state, now() - xact_start as age from pg_stat_activity where datname = 'tshow' and state = 'idle in transaction';` — 결과가 비어 있어야 한다.
- **새 마이그레이션은 파일 맨 위에 `SET LOCAL lock_timeout = '15s';` 를 넣는다.** 락을 못 잡으면 15초 만에 실패해서, 사이트를 오래 막지 않고 배포만 실패한다 (Flyway 는 마이그레이션 하나를 한 트랜잭션으로 실행하므로 `SET LOCAL` 이 적용된다).
- 배포가 헬스체크에서 실패하면 새 슬롯 컨테이너가 재시도로 락을 다시 잡지 않도록 먼저 멈추고(`docker stop tshow-blue` 또는 `tshow-green`) 원인을 본 뒤 다시 배포한다. 멈춘 마이그레이션은 `pg_stat_activity` 에서 `wait_event_type = 'Lock'` 인 `ALTER` 로 찾을 수 있다.
- (선택) `ALTER ROLE tshow SET idle_in_transaction_session_timeout = '10min';` 을 걸면 잊힌 세션이 자동으로 끊긴다.
