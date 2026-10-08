# 배포 가이드 (Oracle Cloud A1 + Jenkins Blue-Green)

tcine과 같은 서버·같은 구조를 쓴다. 공용 인프라(`infra-nginx`, `infra-certbot`, `infra-jenkins`, `infra-postgres`, `infra-qdrant`, 네트워크 `infra-net`)는 이미 있고, tshow는 **자기 앱 컨테이너(`tshow-blue`/`tshow-green`), 자기 데이터베이스, 자기 Nginx 설정, 자기 도메인**만 추가한다.

> 에이전트는 코드와 배포 파일을 만드는 데까지만 한다. 아래 서버 작업, Git push, Jenkins 빌드는 사용자가 직접 한다.

## 구성 파일

| 파일 | 서버에서의 위치 | 역할 |
|---|---|---|
| `Jenkinsfile` | (Jenkins 파이프라인이 읽음) | 체크아웃 → 이미지 빌드 → 새 슬롯 기동 → 헬스체크 → Nginx 전환 → 구 슬롯 중지. 롤백 파라미터 지원 |
| `Dockerfile` | | 멀티스테이지 빌드 (JDK 21 → JRE 21), `/actuator/health` 헬스체크 |
| `deploy/docker-compose.app.yml` | `/data/tshow/docker-compose.app.yml` | `tshow-blue` / `tshow-green` 슬롯 (Jenkins가 복사해 준다) |
| `deploy/nginx/tshow.conf` | `/data/infra/nginx/conf.d/tshow.conf` | `tshow.duckdns.org` 가상 호스트 (80 → 443, 활성 슬롯으로 프록시) |
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

1. **도메인**: DuckDNS에 `tshow` 서브도메인을 만들고 tcine과 같은 서버 IP를 가리키게 한다.

2. **데이터베이스**: 공용 Postgres에 tshow 전용 사용자와 DB를 만든다 (비밀번호는 직접 정한다).
   ```bash
   docker exec -it infra-postgres psql -U tcine -d postgres \
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
- 공용 Qdrant는 yummy·tcine과 함께 쓴다. tshow의 벡터 컬렉션은 이름을 따로 정해서 만든다 (색인 단계에서 추가).
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
