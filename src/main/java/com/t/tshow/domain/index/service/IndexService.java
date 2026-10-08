package com.t.tshow.domain.index.service;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.index.port.Embedder;
import com.t.tshow.domain.index.port.IndexStateStore;
import com.t.tshow.domain.index.port.VectorIndex;
import com.t.tshow.global.config.IndexProperties;
import com.t.tshow.global.util.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 병합된 행사를 벡터 색인에 맞춘다 (docs/index-design.md 2절).
 *
 * <ul>
 *   <li>임베딩 텍스트가 바뀐(또는 처음인) 행사: 다시 임베딩해서 점을 올린다</li>
 *   <li>임베딩 텍스트는 같고 payload(날짜·지역·분류·가격 등)만 바뀐 행사: payload 만 갱신한다 (재임베딩 비용 없음)</li>
 *   <li>논리삭제된 행사, 그리고 DB 에는 없는데 색인에 남은 점: 색인에서 지운다</li>
 * </ul>
 * 일부 실패해도 나머지는 계속하고, 실패한 행사는 상태가 갱신되지 않아 다음 실행에서 다시 시도한다.
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    /** 색인 한 번의 결과 */
    public record Summary(boolean skipped, int embedded, int payloadUpdated, int unchanged, int deleted, int failed) {
        static Summary notRun() {
            return new Summary(true, 0, 0, 0, 0, 0);
        }

        @Override
        public String toString() {
            return skipped ? "건너뜀(임베딩 키 없음 또는 비활성)"
                    : "임베딩 " + embedded + ", payload 갱신 " + payloadUpdated + ", 변경 없음 " + unchanged
                    + ", 삭제 " + deleted + ", 실패 " + failed;
        }
    }

    private final IndexStateStore store;
    private final VectorIndex index;
    private final Embedder embedder;
    private final EmbeddingTextBuilder textBuilder;
    private final PayloadBuilder payloadBuilder;
    private final IndexProperties properties;

    public IndexService(IndexStateStore store, VectorIndex index, Embedder embedder, EmbeddingTextBuilder textBuilder,
                        PayloadBuilder payloadBuilder, IndexProperties properties) {
        this.store = store;
        this.index = index;
        this.embedder = embedder;
        this.textBuilder = textBuilder;
        this.payloadBuilder = payloadBuilder;
        this.properties = properties;
    }

    public Summary sync() {
        if (!properties.enabled() || !embedder.isConfigured()) {
            log.info("색인 {}", Summary.notRun());
            return Summary.notRun();
        }
        index.ensureCollection();

        List<Event> active = store.activeEvents();
        List<Pending> toEmbed = new ArrayList<>();
        List<Pending> toPayload = new ArrayList<>();
        int unchanged = 0;
        for (Event event : active) {
            String text = textBuilder.build(event);
            Map<String, Object> payload = payloadBuilder.build(event);
            Pending p = new Pending(event.getId(), text, Hashes.sha256(text), payload, Hashes.ofPayload(payload));
            if (!event.isIndexed() || !p.embedHash().equals(event.getEmbedHash())) {
                toEmbed.add(p);
            } else if (!p.payloadHash().equals(event.getPayloadHash())) {
                toPayload.add(p);
            } else {
                unchanged++;
            }
        }

        int failed = 0;
        int embedded = 0;
        for (int from = 0; from < toEmbed.size(); from += properties.batchSize()) {
            List<Pending> batch = toEmbed.subList(from, Math.min(from + properties.batchSize(), toEmbed.size()));
            try {
                List<float[]> vectors = embedder.embed(batch.stream().map(Pending::text).toList());
                List<VectorIndex.Point> points = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    points.add(new VectorIndex.Point(batch.get(i).id(), vectors.get(i), batch.get(i).payload()));
                }
                index.upsert(points);
                batch.forEach(p -> store.markIndexed(p.id(), p.embedHash(), p.payloadHash()));
                embedded += batch.size();
            } catch (RuntimeException e) {
                failed += batch.size();
                log.warn("임베딩·색인 묶음 실패({}건): {}", batch.size(), e.getMessage());
            }
        }

        int payloadUpdated = 0;
        for (Pending p : toPayload) {
            try {
                index.overwritePayload(p.id(), p.payload());
                store.markIndexed(p.id(), p.embedHash(), p.payloadHash());
                payloadUpdated++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("payload 갱신 실패 {}: {}", p.id(), e.getMessage());
            }
        }

        int deleted = removeStale(active);
        Summary summary = new Summary(false, embedded, payloadUpdated, unchanged, deleted, failed);
        log.info("색인 완료: {}", summary);
        return summary;
    }

    /** 논리삭제된 행사와, 색인에는 있는데 활성 행사가 아닌 점을 색인에서 지운다 */
    private int removeStale(List<Event> active) {
        Set<UUID> activeIds = new HashSet<>();
        active.forEach(e -> activeIds.add(e.getId()));

        Set<UUID> toDelete = new HashSet<>(store.archivedStillIndexed());
        for (UUID id : index.allIds()) {
            if (!activeIds.contains(id)) toDelete.add(id);
        }
        if (toDelete.isEmpty()) return 0;
        try {
            index.delete(toDelete);
            store.clearIndexed(toDelete);
            return toDelete.size();
        } catch (RuntimeException e) {
            log.warn("색인에서 지우기 실패({}건): {}", toDelete.size(), e.getMessage());
            return 0;
        }
    }

    private record Pending(UUID id, String text, String embedHash, Map<String, Object> payload, String payloadHash) {
    }
}
