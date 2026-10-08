package com.t.tshow.ingest;

import com.t.tshow.ingest.source.SourceType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

/** ingest_run 저장소: 수집 실행 기록과 품질 리포트 */
@Repository
public class IngestRunRepository {

    private final JdbcClient jdbc;

    public IngestRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long start(SourceType source, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                INSERT INTO ingest_run (source, started_at, range_from, range_to, status)
                VALUES (:source, :startedAt, :from, :to, 'RUNNING') RETURNING id
                """)
                .param("source", source.name()).param("startedAt", Timestamp.from(Instant.now()))
                .param("from", Date.valueOf(from)).param("to", Date.valueOf(to))
                .query(Long.class).single();
    }

    public void finish(long id, String status, IngestCounts counts, String unmappedJson, String message) {
        jdbc.sql("""
                UPDATE ingest_run SET finished_at = :finishedAt, status = :status,
                    fetched_count = :fetched, inserted_count = :inserted, updated_count = :updated,
                    skipped_count = :skipped, failed_count = :failed,
                    unmapped = CAST(:unmapped AS jsonb), message = :message
                WHERE id = :id
                """)
                .param("finishedAt", Timestamp.from(Instant.now())).param("status", status)
                .param("fetched", counts.fetched()).param("inserted", counts.inserted()).param("updated", counts.updated())
                .param("skipped", counts.skipped()).param("failed", counts.failed())
                .param("unmapped", unmappedJson).param("message", message).param("id", id)
                .update();
    }
}
