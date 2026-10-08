package com.t.tshow.ingest;

import com.t.tshow.ingest.source.SourceType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** source_record 저장소. (source, source_id) 로 upsert 한다. */
@Repository
public class SourceRecordRepository {

    private final JdbcClient jdbc;

    public SourceRecordRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 소스의 source_id → 마지막 수집 시각. 최근에 수집한 항목의 상세 조회를 건너뛰는 데 쓴다 */
    public Map<String, Instant> fetchedAtBySourceId(SourceType source) {
        Map<String, Instant> result = new HashMap<>();
        jdbc.sql("SELECT source_id, fetched_at FROM source_record WHERE source = :source")
                .param("source", source.name())
                .query((rs, n) -> {
                    Timestamp t = rs.getTimestamp("fetched_at");
                    result.put(rs.getString("source_id"), t == null ? Instant.EPOCH : t.toInstant());
                    return null;
                }).list();
        return result;
    }

    /** 새로 들어왔으면 true, 이미 있어서 갱신했으면 false */
    public boolean upsert(SourceRecord r) {
        return jdbc.sql("""
                INSERT INTO source_record (
                    source, source_id, kind, category, source_category, title, title_norm, description,
                    start_date, end_date, venue_name, venue_name_norm, address, sido_code, sigungu_code, lat, lon,
                    price_type, price_text, age_text, runtime_text, schedule_text, cast_text, host_text,
                    image_url, image_license, info_url, ticket_links, source_updated_at, fetched_at, raw)
                VALUES (
                    :source, :sourceId, :kind, :category, :sourceCategory, :title, :titleNorm, :description,
                    :startDate, :endDate, :venueName, :venueNameNorm, :address, :sidoCode, :sigunguCode, :lat, :lon,
                    :priceType, :priceText, :ageText, :runtimeText, :scheduleText, :castText, :hostText,
                    :imageUrl, :imageLicense, :infoUrl, CAST(:ticketLinks AS jsonb), :sourceUpdatedAt, :fetchedAt, :raw)
                ON CONFLICT (source, source_id) DO UPDATE SET
                    kind = EXCLUDED.kind, category = EXCLUDED.category, source_category = EXCLUDED.source_category,
                    title = EXCLUDED.title, title_norm = EXCLUDED.title_norm, description = EXCLUDED.description,
                    start_date = EXCLUDED.start_date, end_date = EXCLUDED.end_date,
                    venue_name = EXCLUDED.venue_name, venue_name_norm = EXCLUDED.venue_name_norm,
                    address = EXCLUDED.address, sido_code = EXCLUDED.sido_code, sigungu_code = EXCLUDED.sigungu_code,
                    lat = EXCLUDED.lat, lon = EXCLUDED.lon,
                    price_type = EXCLUDED.price_type, price_text = EXCLUDED.price_text, age_text = EXCLUDED.age_text,
                    runtime_text = EXCLUDED.runtime_text, schedule_text = EXCLUDED.schedule_text,
                    cast_text = EXCLUDED.cast_text, host_text = EXCLUDED.host_text,
                    image_url = EXCLUDED.image_url, image_license = EXCLUDED.image_license, info_url = EXCLUDED.info_url,
                    ticket_links = EXCLUDED.ticket_links, source_updated_at = EXCLUDED.source_updated_at,
                    fetched_at = EXCLUDED.fetched_at, raw = EXCLUDED.raw
                RETURNING (xmax = 0) AS inserted
                """)
                .param("source", r.source()).param("sourceId", r.sourceId())
                .param("kind", r.kind()).param("category", r.category()).param("sourceCategory", r.sourceCategory())
                .param("title", r.title()).param("titleNorm", r.titleNorm()).param("description", r.description())
                .param("startDate", r.startDate()).param("endDate", r.endDate())
                .param("venueName", r.venueName()).param("venueNameNorm", r.venueNameNorm())
                .param("address", r.address()).param("sidoCode", r.sidoCode()).param("sigunguCode", r.sigunguCode())
                .param("lat", r.lat()).param("lon", r.lon())
                .param("priceType", r.priceType()).param("priceText", r.priceText())
                .param("ageText", r.ageText()).param("runtimeText", r.runtimeText()).param("scheduleText", r.scheduleText())
                .param("castText", r.castText()).param("hostText", r.hostText())
                .param("imageUrl", r.imageUrl()).param("imageLicense", r.imageLicense()).param("infoUrl", r.infoUrl())
                .param("ticketLinks", r.ticketLinksJson())
                .param("sourceUpdatedAt", r.sourceUpdatedAt() == null ? null : Timestamp.from(r.sourceUpdatedAt().toInstant()))
                .param("fetchedAt", Timestamp.from(r.fetchedAt())).param("raw", r.raw())
                .query(Boolean.class).single();
    }
}
