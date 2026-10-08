package com.t.tshow.domain.ingest.service;

import com.t.tshow.domain.ingest.dto.IngestCounts;
import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.IngestRun;
import com.t.tshow.domain.ingest.entity.RunStatus;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.repository.IngestRunRepository;
import com.t.tshow.domain.ingest.service.normalize.NormalizationReport;
import com.t.tshow.domain.ingest.service.normalize.SourceRecordNormalizer;
import com.t.tshow.domain.ingest.source.EventSource;
import com.t.tshow.domain.ingest.source.IngestContext;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 수집 한 번을 실행한다: 소스 어댑터가 가져온 항목을 정규화해 source_record 에 저장하고, 건수와 "매핑 안 된 값"을 ingest_run 에 남긴다.
 * 소스 하나가 실패해도 다음 소스는 계속한다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final List<EventSource> sources;
    private final SourceRecordNormalizer normalizer;
    private final SourceRecordService records;
    private final IngestRunRepository runs;
    private final IngestProperties properties;

    public IngestService(List<EventSource> sources, SourceRecordNormalizer normalizer, SourceRecordService records,
                         IngestRunRepository runs, IngestProperties properties) {
        this.sources = sources;
        this.normalizer = normalizer;
        this.records = records;
        this.runs = runs;
        this.properties = properties;
    }

    /** 설정의 소스 전체를, 오늘부터 horizon-months 개월 앞까지 수집한다 */
    public void runAll() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(properties.horizonMonths());
        for (EventSource source : sources) {
            if (properties.sources().contains(source.type().name())) {
                run(source, from, to);
            }
        }
    }

    public void run(EventSource source, LocalDate from, LocalDate to) {
        SourceType type = source.type();
        IngestRun run = runs.save(IngestRun.start(type, from, to));
        IngestCounts counts = new IngestCounts();
        NormalizationReport report = new NormalizationReport();
        Map<String, Instant> fetchedAt = records.fetchedAtBySourceId(type);
        Instant freshAfter = Instant.now().minus(Duration.ofHours(properties.refetchAfterHours()));
        log.info("{} 수집 시작: {} ~ {} (이미 저장된 {}건, {}시간 안에 받은 것은 건너뜀)", type, from, to, fetchedAt.size(), properties.refetchAfterHours());

        IngestContext context = new IngestContext() {
            @Override
            public boolean isFresh(SourceType s, String sourceId) {
                Instant t = fetchedAt.get(sourceId);
                return t != null && t.isAfter(freshAfter);
            }

            @Override
            public void emit(RawEvent event) {
                try {
                    boolean inserted = records.upsert(normalizer.normalize(event, Instant.now(), report));
                    counts.recordFetched(inserted);
                    if ((counts.fetched() % 200) == 0) log.info("{} 수집 진행: {}", type, counts);
                } catch (RuntimeException e) {
                    failed(event.source(), event.sourceId(), e);
                }
            }

            @Override
            public void skipped() {
                counts.recordSkipped();
            }

            @Override
            public void failed(SourceType s, String sourceId, Exception cause) {
                counts.recordFailed();
                log.warn("{} {} 항목 실패: {}", s, sourceId, cause.getMessage());
            }
        };

        RunStatus status = RunStatus.SUCCESS;
        String message = null;
        try {
            source.fetch(from, to, context);
            if (counts.failed() > 0) status = RunStatus.PARTIAL;
        } catch (RuntimeException e) {
            status = RunStatus.FAILED;
            message = e.getMessage();
            log.error("{} 수집 중단: {}", type, e.getMessage());
        }
        run.finish(status, counts, report.isEmpty() ? null : Json.write(report.unmapped()), message);
        runs.save(run);
        log.info("{} 수집 {}: {}", type, status, counts);
        if (!report.isEmpty()) log.warn("{} 매핑 안 된 값: {}", type, report.unmapped());
    }
}
