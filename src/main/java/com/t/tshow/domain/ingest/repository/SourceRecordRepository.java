package com.t.tshow.domain.ingest.repository;

import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRecordRepository extends JpaRepository<SourceRecord, Long> {

    Optional<SourceRecord> findBySourceAndSourceId(SourceType source, String sourceId);

    /** 소스의 항목별 마지막 수집 시각. 최근에 수집한 항목의 상세 조회를 건너뛰는 데 쓴다 */
    @Query("select r.sourceId as sourceId, r.fetchedAt as fetchedAt from SourceRecord r where r.source = :source")
    List<FetchedAt> findFetchedAt(@Param("source") SourceType source);

    /** 병합된 행사 하나를 이루는 소스들 (출처 표기용) */
    @Query("select distinct r.source from SourceRecord r where r.id in "
            + "(select l.sourceRecordId from EventSourceLink l where l.eventId = :eventId)")
    List<SourceType> findSourcesOfEvent(@Param("eventId") UUID eventId);

    interface FetchedAt {
        String getSourceId();

        Instant getFetchedAt();
    }
}
