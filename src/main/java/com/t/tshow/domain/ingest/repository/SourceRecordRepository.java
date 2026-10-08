package com.t.tshow.domain.ingest.repository;

import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
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

    /** 조회 기간(from~to)과 겹치는, 제공처 목록에 있어야 하는 소스 레코드 수 */
    @Query("select count(r) from SourceRecord r where r.source = :source and r.startDate <= :to "
            + "and coalesce(r.endDate, r.startDate) >= :from")
    long countInWindow(@Param("source") SourceType source, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 조회 기간과 겹치는 레코드의 "연속으로 목록에 없었던 횟수"를 하나 올린다 (목록에 있었던 것은 뒤이어 0으로 되돌린다) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SourceRecord r set r.missedRuns = r.missedRuns + 1 where r.source = :source and r.startDate <= :to "
            + "and coalesce(r.endDate, r.startDate) >= :from")
    int incrementMissed(@Param("source") SourceType source, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 이번 수집의 목록에 있었던 레코드: 없었던 횟수를 0으로 되돌리고 본 시각을 기록한다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SourceRecord r set r.missedRuns = 0, r.lastSeenAt = :now where r.source = :source and r.sourceId in :ids")
    int markSeen(@Param("source") SourceType source, @Param("ids") Collection<String> ids, @Param("now") Instant now);

    /** 연속으로 목록에 없었던 횟수가 한도에 이른 레코드를 지운다 (제공처가 지운 항목) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SourceRecord r where r.source = :source and r.missedRuns >= :limit")
    int deleteMissed(@Param("source") SourceType source, @Param("limit") int limit);

    /** 끝난 지 오래된 레코드를 지운다 (행사·연결은 함께 정리된다) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SourceRecord r where coalesce(r.endDate, r.startDate) < :cutoff")
    int deleteEndedBefore(@Param("cutoff") LocalDate cutoff);

    interface FetchedAt {
        String getSourceId();

        Instant getFetchedAt();
    }
}
