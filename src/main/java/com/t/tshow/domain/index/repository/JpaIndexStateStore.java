package com.t.tshow.domain.index.repository;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.index.port.IndexStateStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** event 테이블에 색인 상태(embed_hash, payload_hash, indexed_at)를 읽고 쓴다 */
@Repository
public class JpaIndexStateStore implements IndexStateStore {

    private final EventRepository events;

    public JpaIndexStateStore(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> activeEvents() {
        return events.findByArchivedAtIsNull();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> archivedStillIndexed() {
        return events.findArchivedStillIndexedIds();
    }

    @Override
    @Transactional
    public void markIndexed(UUID id, String embedHash, String payloadHash) {
        events.findById(id).ifPresent(e -> e.markIndexed(embedHash, payloadHash, Instant.now()));
    }

    @Override
    @Transactional
    public void clearIndexed(Collection<UUID> ids) {
        events.findAllById(ids).forEach(Event::clearIndexed);
    }
}
