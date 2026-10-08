package com.t.tshow.domain.event.repository;

import com.t.tshow.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** event 저장소. 동적 조건 조회는 {@link EventQueryRepository}(QueryDSL)가 맡는다 */
public interface EventRepository extends JpaRepository<Event, UUID>, EventQueryRepository {

    /** 검색엔진 사이트맵용: 논리삭제되지 않은 행사의 id 와 마지막 수정 시각 */
    @Query("select e.id as id, e.updatedAt as updatedAt from Event e where e.archivedAt is null order by e.updatedAt desc")
    List<SitemapRow> findSitemapRows(org.springframework.data.domain.Pageable limit);

    interface SitemapRow {
        UUID getId();

        Instant getUpdatedAt();
    }

    /** 보관 기간이 지나지 않은(논리삭제되지 않은) 행사 */
    List<Event> findByArchivedAtIsNull();

    /** 논리삭제됐는데 아직 벡터 색인에 올라가 있는 행사 id (색인에서 지울 대상) */
    @Query("select e.id from Event e where e.archivedAt is not null and e.indexedAt is not null")
    List<UUID> findArchivedStillIndexedIds();

    /** 연결된 소스 레코드가 없어진 행사를 지운다 (묶음이 합쳐져 id 가 사라진 경우 등). 지운 수를 돌려준다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Event e where e.id not in (select l.eventId from EventSourceLink l)")
    int deleteOrphans();

    /** 종료 후 보관 기간이 지난 행사를 논리삭제한다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Event e set e.archivedAt = :now where e.archivedAt is null and e.endDate < :cutoff")
    int archiveEndedBefore(@Param("cutoff") LocalDate cutoff, @Param("now") Instant now);

    /** 종료일이 늘어나 다시 보관 기간 안에 든 행사는 되살린다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Event e set e.archivedAt = null where e.archivedAt is not null and (e.endDate is null or e.endDate >= :cutoff)")
    int restoreEndedAfter(@Param("cutoff") LocalDate cutoff);
}
