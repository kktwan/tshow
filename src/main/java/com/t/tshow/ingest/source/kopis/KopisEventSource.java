package com.t.tshow.ingest.source.kopis;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.ingest.http.HttpFetcher;
import com.t.tshow.ingest.http.PoliteHttp;
import com.t.tshow.ingest.normalize.DateParser;
import com.t.tshow.ingest.source.EventSource;
import com.t.tshow.ingest.source.IngestContext;
import com.t.tshow.ingest.source.RawEvent;
import com.t.tshow.ingest.source.SourceType;
import com.t.tshow.ingest.source.TicketLink;
import com.t.tshow.ingest.source.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KOPIS(공연예술통합전산망) 공연 어댑터.
 * 목록(기간 한도마다 나눠서) → 작품별 상세 → 공연장(주소·좌표, 캐시) 순서로 가져와 {@link RawEvent} 로 바꾼다.
 */
@Component
public class KopisEventSource implements EventSource {

    private static final Logger log = LoggerFactory.getLogger(KopisEventSource.class);
    private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IngestProperties.Source config;
    private final HttpFetcher http;
    private final DateParser dates;
    private final ZoneId zone;
    private final String apiKey;

    @Autowired
    public KopisEventSource(IngestProperties properties, DateParser dates, @Value("${kopis.api-key:}") String apiKey) {
        this(properties, dates, apiKey, new PoliteHttp("KOPIS", properties.kopis()));
    }

    /** 테스트에서 가짜 HTTP 를 넣기 위한 생성자 */
    KopisEventSource(IngestProperties properties, DateParser dates, String apiKey, HttpFetcher http) {
        this.config = properties.kopis();
        this.http = http;
        this.dates = dates;
        this.zone = ZoneId.of(properties.zone());
        this.apiKey = apiKey;
    }

    @Override
    public SourceType type() {
        return SourceType.KOPIS;
    }

    @Override
    public void fetch(LocalDate from, LocalDate to, IngestContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("KOPIS_API_KEY 가 설정되지 않았어요");
        }
        Set<String> seen = new HashSet<>();
        Map<String, Facility> facilities = new HashMap<>();

        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = start.plusDays(config.windowDays());
            if (end.isAfter(to)) end = to;
            for (int page = 1; ; page++) {
                String label = "목록 " + start + "~" + end + " " + page + "쪽";
                List<Element> items = Xml.elements(Xml.parse(http.get(listUrl(start, end, page), label)), "db");
                for (Element item : items) {
                    String id = Xml.text(item, "mt20id");
                    if (id == null || !seen.add(id)) continue;
                    if (context.isFresh(SourceType.KOPIS, id)) {
                        context.skipped();
                        continue;
                    }
                    try {
                        context.emit(fetchOne(id, facilities));
                    } catch (RuntimeException e) {
                        context.failed(SourceType.KOPIS, id, e);
                    }
                }
                if (items.size() < config.pageSize()) break;
            }
            start = end.plusDays(1);
        }
    }

    private RawEvent fetchOne(String id, Map<String, Facility> facilities) {
        String detailXml = http.get(url("/pblprfr/" + id, ""), "상세 " + id);
        Element d = Xml.elements(Xml.parse(detailXml), "db").stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("상세 응답이 비어 있어요"));

        String facilityId = Xml.text(d, "mt10id");
        Facility facility = facilityId == null ? null : facilities.computeIfAbsent(facilityId, this::fetchFacility);

        return new RawEvent(
                SourceType.KOPIS, id, Xml.text(d, "prfnm"), Xml.text(d, "sty"), Xml.text(d, "genrenm"),
                dates.parse(Xml.text(d, "prfpdfrom")), dates.parse(Xml.text(d, "prfpdto")),
                facility != null && facility.name() != null ? facility.name() : Xml.text(d, "fcltynm"),
                facility == null ? null : facility.address(),
                Xml.text(d, "area"), null, null, null,
                facility == null ? null : facility.lat(), facility == null ? null : facility.lon(),
                Xml.text(d, "pcseguidance"), Xml.text(d, "prfage"), Xml.text(d, "prfruntime"), Xml.text(d, "dtguidance"),
                Xml.text(d, "prfcast"), Xml.text(d, "entrpsnm"),
                Xml.text(d, "poster"), null, null, ticketLinks(d), updatedAt(Xml.text(d, "updatedate")), detailXml);
    }

    /** 공연장 조회 실패는 공연 수집을 막지 않는다 (주소·좌표 없이 저장되고 다음 수집에서 다시 시도한다) */
    private Facility fetchFacility(String facilityId) {
        try {
            String xml = http.get(url("/prfplc/" + facilityId, ""), "공연장 " + facilityId);
            Element f = Xml.elements(Xml.parse(xml), "db").stream().findFirst().orElse(null);
            if (f == null) return Facility.EMPTY;
            return new Facility(Xml.text(f, "fcltynm"), Xml.text(f, "adres"), Xml.number(f, "la"), Xml.number(f, "lo"));
        } catch (RuntimeException e) {
            log.warn("KOPIS 공연장 {} 조회 실패: {}", facilityId, e.getMessage());
            return Facility.EMPTY;
        }
    }

    private List<TicketLink> ticketLinks(Element detail) {
        List<TicketLink> links = new ArrayList<>();
        for (Element r : Xml.elements(detail, "relate")) {
            String name = Xml.text(r, "relatenm");
            String url = Xml.text(r, "relateurl");
            if (url != null) links.add(new TicketLink(name, url));
        }
        return links;
    }

    private OffsetDateTime updatedAt(String text) {
        if (text == null || text.length() < 19) return null;
        try {
            return LocalDateTime.parse(text.substring(0, 19), UPDATED_AT).atZone(zone).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String listUrl(LocalDate start, LocalDate end, int page) {
        return url("/pblprfr", "&stdate=" + compact(start) + "&eddate=" + compact(end)
                + "&cpage=" + page + "&rows=" + config.pageSize());
    }

    private String url(String path, String query) {
        return config.baseUrl() + path + "?service=" + encodeKey() + query;
    }

    private String encodeKey() {
        return apiKey.contains("%") ? apiKey : URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private static String compact(LocalDate date) {
        return date.toString().replace("-", "");
    }

    private record Facility(String name, String address, Double lat, Double lon) {
        static final Facility EMPTY = new Facility(null, null, null, null);
    }
}
