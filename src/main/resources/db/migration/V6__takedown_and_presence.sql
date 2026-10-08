-- 락을 못 잡으면 15초 만에 실패시킨다 (대기하는 ALTER 가 뒤따르는 조회까지 막아 사이트가 멈추는 것을 막는다)
SET LOCAL lock_timeout = '15s';

-- 삭제 요청 목록: 제공처·저작권자의 요청으로 보이지 않게 할 소스 레코드와 이미지.
-- 병합 단계가 항상 먼저 적용하므로 수집이 매일 다시 돌아도 되살아나지 않는다.
--   RECORD: 이 소스 레코드를 병합에서 제외한다 (그 행사에서 이 소스의 모든 내용이 빠진다)
--   IMAGE : 이 소스 레코드의 이미지만 쓰지 않는다
CREATE TABLE takedown (
    id           BIGSERIAL PRIMARY KEY,
    kind         VARCHAR(20)  NOT NULL,
    source       VARCHAR(20)  NOT NULL,
    source_id    VARCHAR(100) NOT NULL,
    reason       TEXT,
    requested_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_takedown UNIQUE (kind, source, source_id)
);

-- 제공처 API 에서 사라진 항목을 알아내기 위한 값.
--   last_seen_at : 수집 때 API 목록에서 마지막으로 본 시각
--   missed_runs  : 성공한 수집에서 연속으로 목록에 없었던 횟수 (보이면 0 으로 돌아간다)
ALTER TABLE source_record ADD COLUMN last_seen_at TIMESTAMPTZ;
ALTER TABLE source_record ADD COLUMN missed_runs  INT NOT NULL DEFAULT 0;
