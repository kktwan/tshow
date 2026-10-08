-- 벡터 색인 상태: 어떤 내용으로 색인했는지 해시로 기억해서, 바뀐 것만 다시 임베딩하거나 payload 만 갱신한다.
--   embed_hash   임베딩 텍스트의 해시 (바뀌면 다시 임베딩)
--   payload_hash 필터용 payload 의 해시 (바뀌면 payload 만 갱신, 재임베딩 없음)
--   indexed_at   Qdrant 에 올라가 있는 행사만 값이 있다 (논리삭제된 행사를 Qdrant 에서 지울 대상으로 가려내는 데 쓴다)
ALTER TABLE event ADD COLUMN embed_hash   VARCHAR(64);
ALTER TABLE event ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE event ADD COLUMN indexed_at   TIMESTAMPTZ;
