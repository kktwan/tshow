package com.t.tshow.domain.event.repository;

import com.t.tshow.domain.event.entity.EventSourceLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EventSourceLinkRepository extends JpaRepository<EventSourceLink, Long> {

    /** 현재 연결(소스 레코드 → 행사). 엔티티로 읽지 않아 영속성 컨텍스트에 남지 않는다 */
    @Query("select l.sourceRecordId as sourceRecordId, l.eventId as eventId from EventSourceLink l")
    List<LinkRow> findAllLinks();

    interface LinkRow {
        Long getSourceRecordId();

        UUID getEventId();
    }
}
