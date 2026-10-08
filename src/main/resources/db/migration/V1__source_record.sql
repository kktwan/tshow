-- 소스별 정규화 레코드: 각 API에서 가져온 한 건을 공통 형식으로 바꿔 저장한다.
-- 중복 제거(병합)는 이 테이블을 읽어 별도 단계에서 한다. 그래서 API를 다시 호출하지 않고도 병합 규칙만 바꿔 다시 돌릴 수 있다.
CREATE TABLE source_record (
    id                 BIGSERIAL PRIMARY KEY,
    source             VARCHAR(20)  NOT NULL,
    source_id          VARCHAR(100) NOT NULL,
    kind               VARCHAR(20)  NOT NULL,
    category           VARCHAR(40)  NOT NULL,
    source_category    VARCHAR(100),
    title              TEXT         NOT NULL,
    title_norm         TEXT         NOT NULL,
    description        TEXT,
    start_date         DATE,
    end_date           DATE,
    venue_name         TEXT,
    venue_name_norm    TEXT,
    address            TEXT,
    sido_code          VARCHAR(10),
    sigungu_code       VARCHAR(10),
    lat                DOUBLE PRECISION,
    lon                DOUBLE PRECISION,
    price_type         VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    price_text         TEXT,
    age_text           TEXT,
    runtime_text       TEXT,
    schedule_text      TEXT,
    cast_text          TEXT,
    host_text          TEXT,
    image_url          TEXT,
    image_license      VARCHAR(40),
    ticket_links       JSONB,
    source_updated_at  TIMESTAMPTZ,
    fetched_at         TIMESTAMPTZ  NOT NULL,
    raw                TEXT,
    CONSTRAINT uq_source_record UNIQUE (source, source_id)
);
CREATE INDEX idx_source_record_dates ON source_record (end_date, start_date);
CREATE INDEX idx_source_record_dedup ON source_record (title_norm, start_date);
CREATE INDEX idx_source_record_region ON source_record (sido_code, sigungu_code);

-- 수집 실행 기록과 품질 리포트
CREATE TABLE ingest_run (
    id               BIGSERIAL PRIMARY KEY,
    source           VARCHAR(20) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    range_from       DATE,
    range_to         DATE,
    status           VARCHAR(20) NOT NULL,
    fetched_count    INT NOT NULL DEFAULT 0,
    inserted_count   INT NOT NULL DEFAULT 0,
    updated_count    INT NOT NULL DEFAULT 0,
    skipped_count    INT NOT NULL DEFAULT 0,
    failed_count     INT NOT NULL DEFAULT 0,
    unmapped         JSONB,
    message          TEXT
);

-- KOPIS 박스오피스 순위 (집계 안내 표시를 위해 집계 기간과 수집 시각을 함께 저장한다)
CREATE TABLE boxoffice_rank (
    id              BIGSERIAL PRIMARY KEY,
    stat_type       VARCHAR(10)  NOT NULL,
    period_from     DATE         NOT NULL,
    period_to       DATE         NOT NULL,
    category        VARCHAR(100),
    area            VARCHAR(100),
    rank_no         INT          NOT NULL,
    source_id       VARCHAR(100) NOT NULL,
    title           TEXT,
    venue_name      TEXT,
    perf_count      INT,
    seat_count      INT,
    fetched_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_boxoffice UNIQUE (stat_type, period_from, period_to, category, area, rank_no)
);
