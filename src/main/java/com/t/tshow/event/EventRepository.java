package com.t.tshow.event;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** event, event_source 저장소 */
@Repository
public class EventRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public EventRepository(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** 소스 레코드 id → 현재 연결된 행사 id (행사 id 를 안정적으로 이어 쓰는 데 쓴다) */
    public Map<Long, UUID> eventIdBySourceRecordId() {
        Map<Long, UUID> result = new HashMap<>();
        jdbc.sql("SELECT source_record_id, event_id FROM event_source").query((rs, n) -> {
            result.put(rs.getLong("source_record_id"), rs.getObject("event_id", UUID.class));
            return null;
        }).list();
        return result;
    }

    /** 행사 id → 저장된 data_hash (바뀐 행사만 갱신하려고) */
    public Map<UUID, String> dataHashes() {
        Map<UUID, String> result = new HashMap<>();
        jdbc.sql("SELECT id, data_hash FROM event").query((rs, n) -> {
            result.put(rs.getObject("id", UUID.class), rs.getString("data_hash"));
            return null;
        }).list();
        return result;
    }

    public void insert(Event e, Instant now) {
        jdbc.sql("""
                INSERT INTO event (id, kind, category, title, title_norm, description, start_date, end_date,
                    venue_name, venue_name_norm, address, sido_code, sigungu_code, lat, lon,
                    price_type, price_text, age_text, runtime_text, schedule_text, cast_text, host_text,
                    image_url, image_license, info_url, ticket_links, source_count, has_description, data_hash,
                    created_at, updated_at)
                VALUES (:id, :kind, :category, :title, :titleNorm, :description, :startDate, :endDate,
                    :venueName, :venueNameNorm, :address, :sidoCode, :sigunguCode, :lat, :lon,
                    :priceType, :priceText, :ageText, :runtimeText, :scheduleText, :castText, :hostText,
                    :imageUrl, :imageLicense, :infoUrl, CAST(:ticketLinks AS jsonb), :sourceCount, :hasDescription, :dataHash,
                    :now, :now)
                """)
                .paramSource(params(e, now)).update();
    }

    public void update(Event e, Instant now) {
        jdbc.sql("""
                UPDATE event SET kind = :kind, category = :category, title = :title, title_norm = :titleNorm,
                    description = :description, start_date = :startDate, end_date = :endDate,
                    venue_name = :venueName, venue_name_norm = :venueNameNorm, address = :address,
                    sido_code = :sidoCode, sigungu_code = :sigunguCode, lat = :lat, lon = :lon,
                    price_type = :priceType, price_text = :priceText, age_text = :ageText, runtime_text = :runtimeText,
                    schedule_text = :scheduleText, cast_text = :castText, host_text = :hostText,
                    image_url = :imageUrl, image_license = :imageLicense, info_url = :infoUrl,
                    ticket_links = CAST(:ticketLinks AS jsonb), source_count = :sourceCount,
                    has_description = :hasDescription, data_hash = :dataHash, updated_at = :now
                WHERE id = :id
                """)
                .paramSource(params(e, now)).update();
    }

    /** 행사-소스 레코드 연결을 모두 지우고 다시 넣는다 (레코드가 다른 행사로 옮겨 가도 유일 제약에 걸리지 않게) */
    public void replaceAllLinks(Map<UUID, List<Long>> links) {
        template.update("DELETE FROM event_source");
        List<Object[]> rows = new java.util.ArrayList<>();
        links.forEach((eventId, ids) -> ids.forEach(id -> rows.add(new Object[]{eventId, id})));
        template.batchUpdate("INSERT INTO event_source (event_id, source_record_id) VALUES (?, ?)", new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setObject(1, rows.get(i)[0]);
                ps.setLong(2, (Long) rows.get(i)[1]);
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    /** 연결된 소스 레코드가 없어진 행사를 지운다 (묶음이 합쳐져 id 가 사라진 경우 등). 지운 수를 돌려준다 */
    public int deleteOrphans() {
        return jdbc.sql("DELETE FROM event WHERE id NOT IN (SELECT event_id FROM event_source)").update();
    }

    /** 종료 후 보관 기간이 지난 행사를 논리삭제하고, 종료일이 늘어나 다시 보관 기간 안에 든 행사는 되살린다. [논리삭제 수, 되살린 수] */
    public int[] applyRetention(LocalDate cutoff, Instant now) {
        int archived = jdbc.sql("UPDATE event SET archived_at = :now WHERE archived_at IS NULL AND end_date < :cutoff")
                .param("now", Timestamp.from(now)).param("cutoff", Date.valueOf(cutoff)).update();
        int restored = jdbc.sql("UPDATE event SET archived_at = NULL WHERE archived_at IS NOT NULL AND (end_date IS NULL OR end_date >= :cutoff)")
                .param("cutoff", Date.valueOf(cutoff)).update();
        return new int[]{archived, restored};
    }

    private static org.springframework.jdbc.core.namedparam.MapSqlParameterSource params(Event e, Instant now) {
        return new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", e.id()).addValue("kind", e.kind()).addValue("category", e.category())
                .addValue("title", e.title()).addValue("titleNorm", e.titleNorm()).addValue("description", e.description())
                .addValue("startDate", e.startDate() == null ? null : Date.valueOf(e.startDate()))
                .addValue("endDate", e.endDate() == null ? null : Date.valueOf(e.endDate()))
                .addValue("venueName", e.venueName()).addValue("venueNameNorm", e.venueNameNorm())
                .addValue("address", e.address()).addValue("sidoCode", e.sidoCode()).addValue("sigunguCode", e.sigunguCode())
                .addValue("lat", e.lat()).addValue("lon", e.lon())
                .addValue("priceType", e.priceType()).addValue("priceText", e.priceText())
                .addValue("ageText", e.ageText()).addValue("runtimeText", e.runtimeText()).addValue("scheduleText", e.scheduleText())
                .addValue("castText", e.castText()).addValue("hostText", e.hostText())
                .addValue("imageUrl", e.imageUrl()).addValue("imageLicense", e.imageLicense()).addValue("infoUrl", e.infoUrl())
                .addValue("ticketLinks", e.ticketLinksJson()).addValue("sourceCount", e.sourceCount())
                .addValue("hasDescription", e.hasDescription()).addValue("dataHash", e.dataHash())
                .addValue("now", Timestamp.from(now));
    }
}
