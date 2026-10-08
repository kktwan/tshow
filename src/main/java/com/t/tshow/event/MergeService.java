package com.t.tshow.event;

import com.t.tshow.global.config.MergeProperties;
import com.t.tshow.ingest.SourceRecord;
import com.t.tshow.ingest.SourceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 병합 한 번을 실행한다: 저장된 소스 레코드를 읽어 묶고 합쳐 event 에 반영하고, 보관 기간을 적용한다.
 * 소스를 다시 호출하지 않으므로 병합 규칙(설정)만 바꿔 다시 돌릴 수 있다. 한 트랜잭션이라 중간에 실패해도 반쪽 결과가 남지 않는다.
 */
@Service
public class MergeService {

    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    /** 병합 결과 요약 */
    public record Summary(int sourceRecords, int events, int mergedEvents, int created, int updated, int unchanged,
                          int deletedOrphans, int archived, int restored, int conflicts) {
        @Override
        public String toString() {
            return "소스 레코드 " + sourceRecords + " → 행사 " + events + " (여러 소스 병합 " + mergedEvents + ") | 신규 " + created
                    + ", 갱신 " + updated + ", 변경 없음 " + unchanged + ", 정리 " + deletedOrphans
                    + " | 논리삭제 " + archived + ", 복구 " + restored + " | 충돌 " + conflicts;
        }
    }

    private final SourceRecordRepository sourceRecords;
    private final EventRepository events;
    private final MergePlanner planner;
    private final MergeProperties properties;

    public MergeService(SourceRecordRepository sourceRecords, EventRepository events, MergePlanner planner, MergeProperties properties) {
        this.sourceRecords = sourceRecords;
        this.events = events;
        this.planner = planner;
        this.properties = properties;
    }

    @Transactional
    public Summary run() {
        Instant now = Instant.now();
        List<SourceRecord> records = sourceRecords.findAll();
        Map<Long, UUID> existingLinks = events.eventIdBySourceRecordId();
        Map<UUID, String> hashes = events.dataHashes();

        MergePlanner.Plan plan = planner.plan(records, existingLinks, UUID::randomUUID);

        int created = 0, updated = 0, unchanged = 0, merged = 0;
        Map<UUID, List<Long>> links = new HashMap<>();
        for (MergePlanner.PlannedEvent planned : plan.events()) {
            Event event = planned.event();
            String stored = hashes.get(event.id());
            if (stored == null) {
                events.insert(event, now);
                created++;
            } else if (!stored.equals(event.dataHash())) {
                events.update(event, now);
                updated++;
            } else {
                unchanged++;
            }
            if (planned.mergedAcrossSources()) merged++;
            links.put(event.id(), planned.sourceRecordIds());
        }
        events.replaceAllLinks(links);
        int orphans = events.deleteOrphans();
        int[] retention = events.applyRetention(LocalDate.now().minusDays(properties.retentionDays()), now);

        Summary summary = new Summary(records.size(), plan.events().size(), merged, created, updated, unchanged,
                orphans, retention[0], retention[1], plan.conflicts());
        log.info("병합 완료: {}", summary);
        return summary;
    }
}
