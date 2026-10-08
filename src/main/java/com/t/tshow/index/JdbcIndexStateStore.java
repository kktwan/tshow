package com.t.tshow.index;

import com.t.tshow.event.Event;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** event 테이블에 색인 상태(embed_hash, payload_hash, indexed_at)를 읽고 쓴다 */
@Repository
public class JdbcIndexStateStore implements IndexStateStore {

    private final JdbcClient jdbc;

    public JdbcIndexStateStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Stored> activeEvents() {
        return jdbc.sql("SELECT * FROM event WHERE archived_at IS NULL").query((rs, n) -> {
            Event event = new Event(
                    rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("category"), rs.getString("title"),
                    rs.getString("title_norm"), rs.getString("description"),
                    rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                    rs.getString("venue_name"), rs.getString("venue_name_norm"), rs.getString("address"),
                    rs.getString("sido_code"), rs.getString("sigungu_code"),
                    (Double) rs.getObject("lat"), (Double) rs.getObject("lon"),
                    rs.getString("price_type"), rs.getString("price_text"), rs.getString("age_text"),
                    rs.getString("runtime_text"), rs.getString("schedule_text"), rs.getString("cast_text"),
                    rs.getString("host_text"), rs.getString("image_url"), rs.getString("image_license"),
                    rs.getString("info_url"), rs.getString("ticket_links"), rs.getInt("source_count"),
                    rs.getBoolean("has_description"), rs.getString("data_hash"));
            return new Stored(event, rs.getString("embed_hash"), rs.getString("payload_hash"), rs.getTimestamp("indexed_at") != null);
        }).list();
    }

    @Override
    public List<UUID> archivedStillIndexed() {
        return jdbc.sql("SELECT id FROM event WHERE archived_at IS NOT NULL AND indexed_at IS NOT NULL")
                .query((rs, n) -> rs.getObject("id", UUID.class)).list();
    }

    @Override
    public void markIndexed(UUID id, String embedHash, String payloadHash) {
        jdbc.sql("UPDATE event SET embed_hash = :embedHash, payload_hash = :payloadHash, indexed_at = :now WHERE id = :id")
                .param("embedHash", embedHash).param("payloadHash", payloadHash)
                .param("now", Timestamp.from(Instant.now())).param("id", id).update();
    }

    @Override
    public void clearIndexed(Collection<UUID> ids) {
        if (ids.isEmpty()) return;
        jdbc.sql("UPDATE event SET embed_hash = NULL, payload_hash = NULL, indexed_at = NULL WHERE id IN (:ids)")
                .param("ids", ids).update();
    }
}
