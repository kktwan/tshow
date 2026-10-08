package com.t.tshow.domain.ingest.service;

import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.repository.SourceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 정규화한 소스 레코드를 저장하고 읽는다. (source, sourceId) 가 같으면 같은 항목이라 갱신한다. */
@Service
public class SourceRecordService {

    private final SourceRecordRepository records;

    public SourceRecordService(SourceRecordRepository records) {
        this.records = records;
    }

    /** 새로 들어왔으면 true, 이미 있어서 갱신했으면 false */
    @Transactional
    public boolean upsert(SourceRecord incoming) {
        Optional<SourceRecord> existing = records.findBySourceAndSourceId(incoming.getSource(), incoming.getSourceId());
        if (existing.isPresent()) {
            existing.get().refreshFrom(incoming);
            return false;
        }
        records.save(incoming);
        return true;
    }

    /** 소스의 source_id → 마지막 수집 시각. 최근에 수집한 항목의 상세 조회를 건너뛰는 데 쓴다 */
    @Transactional(readOnly = true)
    public Map<String, Instant> fetchedAtBySourceId(SourceType source) {
        Map<String, Instant> result = new HashMap<>();
        for (SourceRecordRepository.FetchedAt row : records.findFetchedAt(source)) {
            result.put(row.getSourceId(), row.getFetchedAt() == null ? Instant.EPOCH : row.getFetchedAt());
        }
        return result;
    }

    /** 제공처 삭제 반영 결과 */
    public record Reconciliation(boolean applied, int deleted, String note) {
    }

    /**
     * 수집이 끝난 뒤 제공처 목록에서 사라진 항목을 가려내 지운다. 조회 기간과 겹치는 레코드 중 이번 목록에 없던 것은
     * "연속으로 없었던 횟수"를 하나 올리고, 있던 것은 0으로 되돌린다. 횟수가 한도(deleteAfterMisses)에 이른 레코드는 삭제한다.
     * 일시 장애로 몇 번 빠지는 경우를 가리려고 연속 횟수를 보고, API 가 비정상적으로 적은 목록을 주면(장애) 판단 자체를 건너뛴다.
     * 성공한 수집에서만 불러야 한다.
     */
    @Transactional
    public Reconciliation reconcile(SourceType source, Set<String> listed, LocalDate from, LocalDate to,
                                    int deleteAfterMisses, double minListedRatio) {
        long expected = records.countInWindow(source, from, to);
        if (!isReliable(expected, listed.size(), minListedRatio)) {
            return new Reconciliation(false, 0, "목록이 너무 적어 판단을 건너뜀 (있어야 할 " + expected + "건, 목록 " + listed.size() + "건)");
        }
        records.incrementMissed(source, from, to);
        Instant now = Instant.now();
        List<String> ids = new ArrayList<>(listed);
        for (int i = 0; i < ids.size(); i += 500) {
            records.markSeen(source, ids.subList(i, Math.min(i + 500, ids.size())), now);
        }
        return new Reconciliation(true, records.deleteMissed(source, deleteAfterMisses), null);
    }

    /** 목록이 믿을 만한가: 있어야 할 항목이 없으면 판단할 게 없어 괜찮고, 있어야 할 항목이 있는데 목록이 비었거나 너무 적으면 장애로 본다 */
    static boolean isReliable(long expected, int listed, double minRatio) {
        return expected == 0 || (listed > 0 && listed >= expected * minRatio);
    }

    /** 끝난 지 오래된(cutoff 보다 먼저 끝난) 소스 레코드를 지운다. 행사와의 연결은 함께 사라지고, 연결이 없어진 행사는 병합이 정리한다 */
    @Transactional
    public int purgeEndedBefore(LocalDate cutoff) {
        return records.deleteEndedBefore(cutoff);
    }

    /** 병합 입력: 저장된 소스 레코드 전체 */
    @Transactional(readOnly = true)
    public List<SourceRecord> findAll() {
        return records.findAll();
    }
}
