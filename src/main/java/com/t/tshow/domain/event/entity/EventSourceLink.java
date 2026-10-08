package com.t.tshow.domain.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * 병합된 행사와 소스 레코드의 연결 (event_source 테이블). 소스 레코드 하나는 행사 하나에만 속한다.
 * 병합을 다시 돌릴 때 통째로 지우고 다시 넣으므로 키는 소스 레코드 id 하나만 쓴다.
 */
@Entity
@Table(name = "event_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSourceLink implements Persistable<Long> {

    @Id
    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    public EventSourceLink(UUID eventId, Long sourceRecordId) {
        this.eventId = eventId;
        this.sourceRecordId = sourceRecordId;
    }

    @Override
    public Long getId() {
        return sourceRecordId;
    }

    /** 연결은 항상 새로 넣는다 (병합 때 전부 지우고 다시 넣는다) */
    @Override
    public boolean isNew() {
        return true;
    }
}
