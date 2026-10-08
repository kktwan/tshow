# TSHOW (공연 · 축제 · 전시 일정)

**서비스 주소: https://tshow.duckdns.org**

공연·축제·전시 일정을 모아 **날짜·지역·분위기**로 찾아 주는 서비스. 실사용이 먼저이고 장기적으로는 수익(광고, 예매 제휴)을 목표로 한다. 날짜·지역·분위기로 검색하고, 버튼을 누르면 AI가 후보 중에서 골라 이유를 설명해 준다("이번 주말 서울에서 아이와 갈 만한 곳"). 이후 관심 조건 알림을 붙인다.

## 현재 상태

| 단계 | 상태 | 문서 |
|---|---|---|
| 수집 (KOPIS · TourAPI · 문화정보원) → 정규화 → `source_record` | 구현됨, 운영에서 전체 수집 완료 | [docs/data-sources.md](docs/data-sources.md) |
| 소스 간 중복 병합 → `event` | 구현됨 | [docs/index-design.md](docs/index-design.md) 4절 |
| 벡터 색인 (Qdrant `tshow-events`) | 구현됨, 병합 뒤 자동 실행 | [docs/index-design.md](docs/index-design.md) 2절 |
| 검색 서비스 · API · 화면 | 구현됨 | [docs/search-design.md](docs/search-design.md) |
| 배포 (Oracle A1 + Jenkins Blue-Green, nginx 호출 제한) | 파일·서버 설정 있음 | [deploy/README.md](deploy/README.md) |
| AI 추천 (Gemini, 버튼을 눌렀을 때만) | 구현됨 (서버에는 `GEMINI_API_KEY` 설정 필요) | [docs/search-design.md](docs/search-design.md) 6절 |
| 박스오피스 수집, 알림 | 아직 없음 | |

## 로컬 실행

1. 인프라 기동 (DB `5434`, Qdrant `6335/6336`)
   ```bash
   docker compose up -d db qdrant
   ```
2. `.env.example`을 복사해 `.env`를 만들고 인증키를 채운다 (`.env`는 커밋하지 않는다). 키: `KOPIS_API_KEY`, `TOURAPI_API_KEY`, `CULTURE_API_KEY`, 임베딩용 `OPENAI_API_KEY`, AI 추천용 `GEMINI_API_KEY`(없으면 AI 추천 버튼만 숨겨진다).
3. 빌드·실행
   ```bash
   ./gradlew.bat build
   ./gradlew.bat bootRun
   ```
   화면 `http://localhost:8080/`, 헬스체크 `http://localhost:8080/actuator/health`

**로컬 수집**: 환경변수 `TSHOW_INGEST_ON_STARTUP=true`, `TSHOW_INGEST_SOURCES=KOPIS,CULTURE,TOURAPI`, `TSHOW_INGEST_HORIZON_MONTHS=...` 를 주고 앱을 실행한다(`application.yml`의 `tshow.ingest.*`). 수집이 끝나면 병합·색인이 이어서 돈다. 결과는 `ingest_run` 테이블에서 본다. 로컬에는 개발용으로 일부만 수집해 둔다. 전체(6개월) 수집은 운영 서버에서 한다(KOPIS는 몇 시간).

**테스트**: `./gradlew.bat test --no-daemon` (정규화, 세 소스 어댑터, 중복 병합, 벡터 색인, 공통 호출, 질의 해석, 검색 서비스, API 응답). 실제 Qdrant 통합 테스트는 `RUN_QDRANT_IT=true`, 검색 평가 세트는 `RUN_SEARCH_EVAL=true` 일 때만 돈다.

## 협업 규칙

- 에이전트는 코드 수정과 로컬 빌드·테스트까지만 한다. Git push, Jenkins, 서버 배포, 키 발급·신청은 사용자가 직접 한다.
- 검색·추천이 이상할 때 **그 검색어만 맞추는 땜질을 하지 않는다.** 원인(필터/색인 데이터/점수/AI 지시문)을 찾아 고친다.
- **평가 세트로 고치기 전후를 숫자로 비교한다** (`src/test/resources/search-eval/cases.yml`).
- 인증키·비밀번호·서버 주소는 소스, 문서, 커밋, 화면 캡처에 남기지 않는다. `.env`로 관리하고 `.env`는 커밋하지 않는다.
- 응답은 한국어로 간결하게, 실행 명령은 복사해서 쓸 수 있게 코드 블록으로 준다.

## 데이터 소스

모두 공공데이터포털(data.go.kr)의 무료 OpenAPI. 활용신청은 사용자가 직접 하고 인증키를 받는다.

| 데이터 | 제공처 | 용도 |
|---|---|---|
| 공연 | 공연예술통합전산망 KOPIS(예술경영지원센터) | 공연 목록·일정·공연장 |
| 축제·행사 | 한국관광공사 TourAPI 행사정보 | 지역 축제·문화행사 |
| 공연·전시 | 한국문화정보원 문화포털 | 전시 목록 등 |

엔드포인트, 소스별 차이, 호출 한도, 이미지 이용 조건 등 미확인 항목은 [docs/data-sources.md](docs/data-sources.md)에 있다. TourAPI·문화포털의 이용 조건(출처 표시, 공공누리 유형 등)은 아직 모른다.

### KOPIS 이용 조건 (신청 화면에서 확인)

- **서비스에 반드시 명시**: 집계기간(최종집계 `YYYY.MM.DD`), 집계대상(모든 공연 데이터 전송기관), "집계 데이터는 연계기관 티켓판매시스템에서 발권된 분량 기준이라 해당 공연의 전체 관객 수와 다를 수 있음". 예매·관객 통계를 보여 줄 때 필수이고, 일정 목록만 보여 줘도 푸터에 출처(공연예술통합전산망 KOPIS)를 표시한다. 수집할 때 최종 집계일도 저장한다.
- 인증키는 신청 직후 이메일로 발급된다. 유효기간 1년(연장은 `kopis@gokams.or.kr`로 메일 신청), **3개월 이상 미사용하면 승인이 취소**된다 → 매일 수집 스케줄러가 계속 호출해야 한다. 발급일을 기록해 갱신 시점을 챙긴다.
- 개인정보는 목적에 맞게만 활용한다는 동의 문구가 있다. 사용자 계정·알림 기능을 만들 때 개인정보 처리 방침을 함께 고려한다.

## 설계 원칙

추천·검색 서비스에서 흔한 문제(소스마다 다른 분류 체계, 후처리 필터로 인한 재현율 저하, 조건어가 제목 검색어로 쓰임)를 막으려고 색인 전에 정했다.

1. **필수 조건은 벡터 검색 전에 거른다.** 날짜 범위·지역·카테고리·종료 여부·가격·반경은 Qdrant payload 필터로 후보를 먼저 줄이고 그 안에서 의미 검색한다. (상위 N건을 뽑은 뒤 거르면 조건에 맞는 항목이 후보에 못 들어온다.)
2. **임베딩 텍스트와 필터용 값을 분리한다.** 임베딩 텍스트: 제목, 카테고리, 장소 이름, 출연·주최, 설명. **날짜·가격은 넣지 않는다** — 일정이 바뀌어도 재임베딩이 필요 없고 payload만 갱신하면 된다. payload: 시작·종료일(숫자), 지역 코드(시도/시군구), 위경도, 표준 카테고리, 가격 구분, 출처 수 등.
3. **색인할 때 한 번 정규화한다.** 세 출처의 카테고리를 우리 표준 분류로 변환하는 표를 두고, 지역은 표준 코드로 통일하고, 소개글의 HTML은 걷어낸다. 검색은 표준 값만 본다.
4. **중복과 갱신**: 같은 공연이 KOPIS와 문화포털에 중복될 수 있으므로 제목·장소·기간 기반 중복 판정 규칙을 둔다. 안정 ID로 upsert하고, 일정·상태 변경은 payload만 갱신한다.
5. **순위 기준은 일정에 맞춘다.** "곧 시작/진행 중", 거리, 마감 임박. 점수는 코드에 박지 않고 설정값으로 분리한다.
6. **관련도 하한을 처음부터 둔다.** 벡터는 항상 상위 N건을 돌려주므로, 관련 없는 질의에는 결과를 비우고 안내한다 (`tshow.search.min-score`).

## 코드·패키지 원칙

1. **하드코딩을 왠만하면 하지 않는다.** 분류 변환표, 지역 이름 → 법정동 코드 표, 검색어 사전, 불용어, 점수·임계값은 코드가 아니라 **리소스 파일(yml/csv)이나 `@ConfigurationProperties`** 로 둔다. 새 소스·분류·표현이 생기면 코드가 아니라 데이터를 고친다.
2. **색인 설계를 가장 먼저, 가장 신경 써서 한다.** 설계는 `docs/`에 남긴다.
3. **소스별 차이는 어댑터로 가두고, 나머지는 공통 파이프라인으로 처리한다.** 세 소스는 각자 클라이언트와 매퍼만 갖고 공통 모델(`RawEvent` → 정규화 → `Event`)로 변환한 뒤, 정규화·중복 제거·색인·검색은 소스를 모르는 공통 코드가 한다.
4. **패키지는 책임 단위로, 계층은 `controller / dto / entity / repository / service`로 나눈다**. 현재 구조:
   ```
   com.t.tshow
   ├─ global                공통
   │  ├─ config             @ConfigurationProperties(Ingest/Merge/Index/Normalize/Search), QueryDSL, 스케줄링, 시계
   │  ├─ response           ApiResponse(공통 응답), PageResponse(공통 페이지)
   │  ├─ exception          ErrorCode, BusinessException, GlobalExceptionHandler(API), ViewExceptionHandler(화면)
   │  └─ util               Texts, Dates, Geo, Hashes, Html, Json, Xml (여러 곳에서 쓰는 도우미)
   ├─ infra                 외부 연동 (도메인은 이 안을 모른다)
   │  ├─ http               ApiClient(공통 호출 창구), ApiRequest(주소·파라미터·키 인코딩), RestApiClient(간격·재시도), ApiPolicy
   │  ├─ source/{kopis,culture,tourapi}   AbstractEventSource(공통 뼈대) + 소스별 어댑터
   │  └─ qdrant, embedding, ai   VectorIndex / Embedder / AiCurator(Gemini) 구현
   ├─ domain
   │  ├─ ingest             수집·정규화: entity(SourceRecord, IngestRun), dto(RawEvent…), repository, service(+normalize)
   │  ├─ event              병합 결과: entity(Event, EventSourceLink), dto, repository(JPA + QueryDSL), service(+merge), controller(/events/{id}, /api/events/{id})
   │  ├─ index              벡터 색인: port(VectorIndex, Embedder, IndexStateStore), service, repository(JpaIndexStateStore)
   │  ├─ search             검색: dto, service(QueryAnalyzer, SearchService, SearchDictionary), controller(화면 /, API /api/search)
   │  └─ recommend          AI 추천: port(AiCurator), dto, service(RecommendService, 한도·캐시), controller(/recommend, /api/recommend)
   ├─ batch                 수집 → 병합 → 색인 파이프라인(service)과 cron(scheduler)
   ```
   - 저장소는 **JPA(Hibernate) + Spring Data**, 조건이 바뀌는 조회는 **QueryDSL**(`EventQueryRepository`). 스키마는 Flyway SQL로만 바꾸고 Hibernate는 `ddl-auto: validate`로 맞는지만 확인한다.
   - REST 컨트롤러는 항상 `ApiResponse`로 응답하고, 오류는 `BusinessException(ErrorCode)`를 던져 `GlobalExceptionHandler`가 상태 코드와 함께 `ApiResponse.error`로 바꾼다.
   - 외부 API 호출은 모두 `ApiClient`/`ApiRequest`를 거친다 (호출 간격·재시도·키 인코딩·로그 마스킹을 한 곳에서). 새 소스는 `AbstractEventSource`를 상속해 주소와 응답 읽기만 구현한다.
   - 도메인이 외부 연동을 직접 알지 않게 `port` 인터페이스를 두고 `infra`가 구현한다 (테스트는 가짜 구현).
5. 점수·임계값·가중치는 `@ConfigurationProperties`로 두고 기본값과 이유를 주석으로 남긴다. 어휘·분류표는 리소스 파일 하나에서 관리하고 테스트로 검증한다.
6. 변경은 **평가 세트로 전후를 비교**하고, 이상한 결과는 사례별 땜질이 아니라 원인을 고친다.

## 확정된 결정

- **서비스 지역은 처음부터 전국**. 지역은 법정동 코드(시도 + 시군구)로 정규화하고 좌표를 함께 저장한다. 수집기에는 호출 간격 제한과 재시도를 두고, 첫 수집 뒤에는 변경분만 갱신한다.
- **끝난 일정은 혼합안**: Postgres는 보관 기간(`tshow.retention.days`, 시작값 30일) 후 **논리삭제**(`archived_at`), Qdrant는 같은 시점에 **물리 삭제**. KOPIS 약관에 보관 제한이 있으면 Postgres도 물리 삭제로 바꾼다. 자세한 내용은 `docs/index-design.md`의 "보관과 폐기".
- **이미지는 보여 준다**. 소스가 준 HTTPS URL을 그대로 표시하고 수정하지 않으며 출처를 표기한다. 이용 조건 확인 필요 항목은 `docs/index-design.md`의 "이미지" 절.
- **수집 범위는 오늘부터 6개월 앞** (`tshow.ingest.horizon-months`). KOPIS는 조회 기간이 시작일+30일까지라 30일 단위로 나눠 호출하고, 호출이 몰리면 `400`으로 일시 차단되므로 호출 간격 제한과 재시도를 둔다.
- **KOPIS 박스오피스(관객 통계)를 사용한다**. 화면에 집계기간·집계대상·"전체 관객 수와 다를 수 있음" 안내를 반드시 표시한다.
- **검색 호출 제한**: nginx `limit_req` 로 검색어가 있는 요청은 IP당 분당 30번(`deploy/README.md`). 검색어 길이는 `tshow.search.max-query-length`.

## 미정 결정 (사용자 확인 필요)

- 분위기·대상(아이·연인·가족·반려동물) 태그를 규칙만으로 만들지, AI로 한 번 태깅할지 (추천: 샘플 확인 후 AI 태깅)
- 키워드 검색을 결합한 하이브리드(RRF) 도입 여부, AI 추천 품질 평가 방식(호출 비용·비결정성)

## 남은 일

- 평가 세트 확대와 점수 하한 재조정 (현재 0.30, 여유가 크지 않다)
- 시군구 선택 화면, 박스오피스 수집, 관심 조건 알림, AI 추천 품질 점검
- KOPIS 이용 조건(보관·이미지) 재확인, 재수집 주기를 시간 기준으로 개선

## 기술 스택

Spring Boot 4.1 (Java 21), PostgreSQL 17, Spring Data JPA + QueryDSL, Flyway, Qdrant(OpenAI 임베딩 `text-embedding-3-small` 768차원), Thymeleaf, AI 추천은 Google Gemini(`gemini-3.1-flash-lite-preview`, Spring AI). 배포는 Oracle Cloud A1 + Jenkins Blue-Green, 도메인은 DuckDNS.
