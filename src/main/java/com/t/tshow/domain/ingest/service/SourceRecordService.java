package com.t.tshow.domain.ingest.service;

import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.repository.SourceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    /** 병합 입력: 저장된 소스 레코드 전체 */
    @Transactional(readOnly = true)
    public List<SourceRecord> findAll() {
        return records.findAll();
    }
}
