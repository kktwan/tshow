package com.t.tshow.ingest.source.culture;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.ingest.http.HttpFetcher;
import com.t.tshow.ingest.http.PoliteHttp;
import com.t.tshow.ingest.normalize.DateParser;
import com.t.tshow.ingest.source.EventSource;
import com.t.tshow.ingest.source.IngestContext;
import com.t.tshow.ingest.source.RawEvent;
import com.t.tshow.ingest.source.SourceType;
import com.t.tshow.ingest.source.Xml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한국문화정보원 한눈에보는문화정보조회서비스(공연·전시) 어댑터.
 * 기간별 목록(period2)으로 대상을 찾고, 작품마다 상세(detail2)에서 가격·설명·주소·링크를 가져온다.
 */
@Component
public class CultureEventSource implements EventSource {

    private final IngestProperties.Source config;
    private final HttpFetcher http;
    private final DateParser dates;
    private final String apiKey;

    @Autowired
    public CultureEventSource(IngestProperties properties, DateParser dates, @Value("${culture.api-key:}") String apiKey) {
        this(properties, dates, apiKey, new PoliteHttp("CULTURE", properties.culture()));
    }

    /** 테스트에서 가짜 HTTP 를 넣기 위한 생성자 */
    CultureEventSource(IngestProperties properties, DateParser dates, String apiKey, HttpFetcher http) {
        this.config = properties.culture();
        this.http = http;
        this.dates = dates;
        this.apiKey = apiKey;
    }

    @Override
    public SourceType type() {
        return SourceType.CULTURE;
    }

    @Override
    public void fetch(LocalDate from, LocalDate to, IngestContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CULTURE_API_KEY 가 설정되지 않았어요");
        }
        Set<String> seen = new HashSet<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = start.plusDays(config.windowDays());
            if (end.isAfter(to)) end = to;
            for (int page = 1; ; page++) {
                String label = "목록 " + start + "~" + end + " " + page + "쪽";
                List<Element> items = Xml.elements(Xml.parse(http.get(listUrl(start, end, page), label)), "item");
                for (Element item : items) {
                    String id = Xml.text(item, "seq");
                    if (id == null || !seen.add(id)) continue;
                    if (context.isFresh(SourceType.CULTURE, id)) {
                        context.skipped();
                        continue;
                    }
                    try {
                        context.emit(fetchOne(id, item));
                    } catch (RuntimeException e) {
                        context.failed(SourceType.CULTURE, id, e);
                    }
                }
                if (items.size() < config.pageSize()) break;
            }
            start = end.plusDays(1);
        }
    }

    private RawEvent fetchOne(String id, Element listItem) {
        String detailXml = http.get(url("/detail2", "&seq=" + id), "상세 " + id);
        Element d = Xml.elements(Xml.parse(detailXml), "item").stream().findFirst().orElse(listItem);

        String thumbnail = Xml.text(listItem, "thumbnail");
        return new RawEvent(
                SourceType.CULTURE, id, firstOf(Xml.text(d, "title"), Xml.text(listItem, "title")),
                Xml.text(d, "contents1"), Xml.text(listItem, "realmName"),
                dates.parse(firstOf(Xml.text(d, "startDate"), Xml.text(listItem, "startDate"))),
                dates.parse(firstOf(Xml.text(d, "endDate"), Xml.text(listItem, "endDate"))),
                firstOf(Xml.text(d, "place"), Xml.text(listItem, "place")), Xml.text(d, "placeAddr"),
                firstOf(Xml.text(d, "area"), Xml.text(listItem, "area")),
                firstOf(Xml.text(d, "sigungu"), Xml.text(listItem, "sigungu")), null, null,
                firstNumber(d, listItem, "gpsY"), firstNumber(d, listItem, "gpsX"),
                Xml.text(d, "price"), null, null, null, null, null,
                firstOf(Xml.text(d, "imgUrl"), thumbnail), null, Xml.text(d, "url"), List.of(), null, detailXml);
    }

    private static Double firstNumber(Element primary, Element fallback, String tag) {
        Double v = Xml.number(primary, tag);
        return v != null ? v : Xml.number(fallback, tag);
    }

    private static String firstOf(String a, String b) {
        return a != null ? a : b;
    }

    private String listUrl(LocalDate start, LocalDate end, int page) {
        return url("/period2", "&PageNo=" + page + "&numOfrows=" + config.pageSize() + "&serviceTp=A&from=" + compact(start)
                + "&to=" + compact(end) + "&sortStdr=1");
    }

    private String url(String path, String query) {
        return config.baseUrl() + path + "?serviceKey=" + encodeKey() + query;
    }

    private String encodeKey() {
        return apiKey.contains("%") ? apiKey : URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private static String compact(LocalDate date) {
        return date.toString().replace("-", "");
    }
}
