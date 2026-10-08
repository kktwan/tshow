-- 병합된 행사: 여러 소스의 같은 행사를 하나로 합친 결과. 검색·색인은 이 테이블을 쓴다.
-- 설계: docs/index-design.md (공통 모델, 보관과 폐기)
CREATE TABLE event (
    id                UUID PRIMARY KEY,
    kind              VARCHAR(20)  NOT NULL,
    category          VARCHAR(40)  NOT NULL,
    title             TEXT         NOT NULL,
    title_norm        TEXT         NOT NULL,
    description       TEXT,
    start_date        DATE,
    end_date          DATE,
    venue_name        TEXT,
    venue_name_norm   TEXT,
    address           TEXT,
    sido_code         VARCHAR(10),
    sigungu_code      VARCHAR(10),
    lat               DOUBLE PRECISION,
    lon               DOUBLE PRECISION,
    price_type        VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    price_text        TEXT,
    age_text          TEXT,
    runtime_text      TEXT,
    schedule_text     TEXT,
    cast_text         TEXT,
    host_text         TEXT,
    image_url         TEXT,
    image_license     VARCHAR(40),
    info_url          TEXT,
    ticket_links      JSONB,
    source_count      INT          NOT NULL DEFAULT 1,
    has_description  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 병합 결과 필드의 해시. 바뀐 행사만 갱신하고, 이후 색인 단계가 변경분을 가려내는 데 쓴다
    data_hash         VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    -- 종료 후 보관 기간이 지나면 논리삭제한다 (Qdrant 는 같은 시점에 물리 삭제)
    archived_at       TIMESTAMPTZ
);
CREATE INDEX idx_event_dates ON event (end_date, start_date) WHERE archived_at IS NULL;
CREATE INDEX idx_event_region ON event (sido_code, sigungu_code) WHERE archived_at IS NULL;
CREATE INDEX idx_event_category ON event (category) WHERE archived_at IS NULL;

-- 병합된 행사와 소스 레코드의 연결. 소스 레코드 하나는 행사 하나에만 속한다.
CREATE TABLE event_source (
    event_id          UUID   NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    source_record_id  BIGINT NOT NULL REFERENCES source_record (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, source_record_id),
    CONSTRAINT uq_event_source_record UNIQUE (source_record_id)
);
