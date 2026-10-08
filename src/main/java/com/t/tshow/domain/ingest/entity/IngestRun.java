package com.t.tshow.domain.ingest.entity;

import com.t.tshow.domain.ingest.dto.IngestCounts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/** 수집 실행 기록과 품질 리포트 (ingest_run 테이블의 한 행) */
@Entity
@Table(name = "ingest_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "range_from")
    private LocalDate rangeFrom;

    @Column(name = "range_to")
    private LocalDate rangeTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** 매핑 안 된 값 리포트 (jsonb) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String unmapped;

    private String message;

    public static IngestRun start(SourceType source, LocalDate from, LocalDate to) {
        IngestRun run = new IngestRun();
        run.source = source;
        run.startedAt = Instant.now();
        run.rangeFrom = from;
        run.rangeTo = to;
        run.status = RunStatus.RUNNING;
        return run;
    }

    public void finish(RunStatus status, IngestCounts counts, String unmappedJson, String message) {
        this.finishedAt = Instant.now();
        this.status = status;
        this.fetchedCount = counts.fetched();
        this.insertedCount = counts.inserted();
        this.updatedCount = counts.updated();
        this.skippedCount = counts.skipped();
        this.failedCount = counts.failed();
        this.unmapped = unmappedJson;
        this.message = message;
    }
}
