package com.t.tshow.domain.event.service;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.entity.EventSourceLink;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.event.repository.EventSourceLinkRepository;
import com.t.tshow.domain.event.repository.TakedownRepository;
import com.t.tshow.domain.event.service.merge.TakedownFilter;
import com.t.tshow.domain.event.service.merge.MergePlanner;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.service.SourceRecordService;
import com.t.tshow.global.config.MergeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
                          int deletedOrphans, int archived, int restored, int conflicts,
                          int purgedRecords, int takedownExcluded, int imagesRemoved) {
        @Override
        public String toString() {
            return "소스 레코드 " + sourceRecords + " → 행사 " + events + " (여러 소스 병합 " + mergedEvents + ") | 신규 " + created
                    + ", 갱신 " + updated + ", 변경 없음 " + unchanged + ", 정리 " + deletedOrphans
                    + " | 논리삭제 " + archived + ", 복구 " + restored + " | 충돌 " + conflicts
                    + " | 종료 정리 " + purgedRecords + ", 삭제 요청 제외 " + takedownExcluded + " (이미지 " + imagesRemoved + ")";
        }
    }

    private final SourceRecordService sourceRecords;
    private final EventRepository events;
    private final EventSourceLinkRepository links;
    private final TakedownRepository takedowns;
    private final TakedownFilter takedownFilter;
    private final MergePlanner planner;
    private final MergeProperties properties;

    public MergeService(SourceRecordService sourceRecords, EventRepository events, EventSourceLinkRepository links,
                        TakedownRepository takedowns, TakedownFilter takedownFilter, MergePlanner planner, MergeProperties properties) {
        this.sourceRecords = sourceRecords;
        this.events = events;
        this.links = links;
        this.takedowns = takedowns;
        this.takedownFilter = takedownFilter;
        this.planner = planner;
        this.properties = properties;
    }

    @Transactional
    public Summary run() {
        Instant now = Instant.now();
        // 종료 후 오래된 소스 레코드는 DB 에서도 완전히 지운다 (행사의 보관 기간(retentionDays)이 지난 뒤에만 의미가 있도록 더 크게 잡는다)
        int purgeDays = Math.max(properties.purgeDays(), properties.retentionDays() + 1);
        int purged = sourceRecords.purgeEndedBefore(LocalDate.now().minusDays(purgeDays));

        // 삭제 요청 목록을 먼저 적용한다: 요청받은 소스 레코드는 빼고, 이미지만 요청받은 것은 이미지를 뺀다
        TakedownFilter.Result applied = takedownFilter.apply(sourceRecords.findAll(), takedowns.findAll());
        List<SourceRecord> records = applied.records();

        // 행사 id 를 안정적으로 이어 쓰려고 현재 연결(소스 레코드 → 행사)을 읽어 둔다
        Map<Long, UUID> existingLinks = new HashMap<>();
        links.findAllLinks().forEach(l -> existingLinks.put(l.getSourceRecordId(), l.getEventId()));
        Map<UUID, Event> existing = new HashMap<>();
        events.findAll().forEach(e -> existing.put(e.getId(), e));

        MergePlanner.Plan plan = planner.plan(records, existingLinks, UUID::randomUUID);

        int created = 0, updated = 0, unchanged = 0, merged = 0;
        List<EventSourceLink> newLinks = new ArrayList<>();
        for (MergePlanner.PlannedEvent planned : plan.events()) {
            Event event = planned.event();
            Event stored = existing.get(event.getId());
            if (stored == null) {
                event.created(now);
                events.save(event);
                created++;
            } else if (!stored.getDataHash().equals(event.getDataHash())) {
                stored.applyMerged(event, now);
                updated++;
            } else {
                unchanged++;
            }
            if (planned.mergedAcrossSources()) merged++;
            planned.sourceRecordIds().forEach(recordId -> newLinks.add(new EventSourceLink(event.getId(), recordId)));
        }
        // 레코드가 다른 행사로 옮겨 가도 유일 제약에 걸리지 않게 연결을 모두 지우고 다시 넣는다
        links.deleteAllInBatch();
        links.saveAll(newLinks);

        int orphans = events.deleteOrphans();
        LocalDate cutoff = LocalDate.now().minusDays(properties.retentionDays());
        int archived = events.archiveEndedBefore(cutoff, now);
        int restored = events.restoreEndedAfter(cutoff);

        Summary summary = new Summary(records.size(), plan.events().size(), merged, created, updated, unchanged,
                orphans, archived, restored, plan.conflicts(), purged, applied.excluded(), applied.imagesRemoved());
        log.info("병합 완료: {}", summary);
        return summary;
    }
}
