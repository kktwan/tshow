package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.ingest.entity.SourceRecord;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 소스 레코드를 묶고 합쳐서 "저장할 행사 목록"을 계산한다. DB 를 모르는 순수 계산이라 단위 테스트로 검증한다.
 *
 * <p>행사 id 는 안정적이어야 한다(벡터 색인의 point id 가 되므로). 묶음에 이미 행사에 연결된 레코드가 있으면 그 행사 id 를 이어 쓰고,
 * 여럿이면 가장 작은 id 를 쓴다. 같은 id 를 두 묶음에 주지 않는다 (묶음이 갈라졌을 때 한쪽은 새 id).
 */
@Component
public class MergePlanner {

    /** 저장할 행사와 그 행사에 속한 소스 레코드 id */
    public record PlannedEvent(Event event, List<Long> sourceRecordIds) {
        public boolean mergedAcrossSources() {
            return sourceRecordIds.size() > 1;
        }
    }

    public record Plan(List<PlannedEvent> events, int conflicts) {
    }

    private final Clusterer clusterer;
    private final EventMerger merger;

    public MergePlanner(Clusterer clusterer, EventMerger merger) {
        this.clusterer = clusterer;
        this.merger = merger;
    }

    public Plan plan(List<SourceRecord> records, Map<Long, UUID> existingLinks, Supplier<UUID> newId) {
        Clusterer.Result clusters = clusterer.cluster(records);
        Set<UUID> used = new HashSet<>();
        List<PlannedEvent> planned = new ArrayList<>();

        // 기존 id 를 이어 쓰는 묶음이 먼저 id 를 차지하도록, 이어 쓸 id 가 있는 묶음을 앞에 둔다 (결과 순서와 무관하게 id 가 안정적이다)
        List<List<SourceRecord>> ordered = new ArrayList<>(clusters.clusters());
        ordered.sort((a, b) -> Boolean.compare(existingId(b, existingLinks, Set.of()) != null, existingId(a, existingLinks, Set.of()) != null));

        for (List<SourceRecord> members : ordered) {
            UUID id = existingId(members, existingLinks, used);
            if (id == null) id = newId.get();
            used.add(id);
            Event event = merger.merge(members).toBuilder().id(id).build();
            planned.add(new PlannedEvent(event, members.stream().map(SourceRecord::getId).toList()));
        }
        return new Plan(planned, clusters.conflicts());
    }

    /** 묶음의 레코드가 이미 연결된 행사 id 중 아직 쓰이지 않은 가장 작은 것. 없으면 null */
    private static UUID existingId(List<SourceRecord> members, Map<Long, UUID> existingLinks, Set<UUID> used) {
        UUID best = null;
        for (SourceRecord r : members) {
            UUID id = r.getId() == null ? null : existingLinks.get(r.getId());
            if (id != null && !used.contains(id) && (best == null || id.compareTo(best) < 0)) best = id;
        }
        return best;
    }
}
