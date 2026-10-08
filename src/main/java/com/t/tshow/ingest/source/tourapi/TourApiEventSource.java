package com.t.tshow.ingest.source.tourapi;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.ingest.http.HttpFetcher;
import com.t.tshow.ingest.http.PoliteHttp;
import com.t.tshow.ingest.normalize.DateParser;
import com.t.tshow.ingest.source.EventSource;
import com.t.tshow.ingest.source.IngestContext;
import com.t.tshow.ingest.source.RawEvent;
import com.t.tshow.ingest.source.SourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국관광공사 TourAPI(국문 관광정보서비스) 행사 어댑터.
 * 행사 목록(searchFestival2)은 시작일 기준으로만 조회되므로 lookback-days 만큼 과거부터 가져와 조회 기간과 겹치는 것만 남기고,
 * 작품마다 공통 상세(detailCommon2: 소개글·홈페이지)와 소개 상세(detailIntro2: 주최·시간·요금·연령)를 가져온다.
 */
@Component
public class TourApiEventSource implements EventSource {

    private static final DateTimeFormatter MODIFIED_AT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>]+");

    private final IngestProperties.Source config;
    private final HttpFetcher http;
    private final DateParser dates;
    private final ZoneId zone;
    private final String appName;
    private final String apiKey;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public TourApiEventSource(IngestProperties properties, DateParser dates, @Value("${tourapi.api-key:}") String apiKey) {
        this(properties, dates, apiKey, new PoliteHttp("TOURAPI", properties.tourapi()));
    }

    /** 테스트에서 가짜 HTTP 를 넣기 위한 생성자 */
    TourApiEventSource(IngestProperties properties, DateParser dates, String apiKey, HttpFetcher http) {
        this.config = properties.tourapi();
        this.http = http;
        this.dates = dates;
        this.zone = ZoneId.of(properties.zone());
        this.appName = properties.appName();
        this.apiKey = apiKey;
    }

    @Override
    public SourceType type() {
        return SourceType.TOURAPI;
    }

    @Override
    public void fetch(LocalDate from, LocalDate to, IngestContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TOURAPI_API_KEY 가 설정되지 않았어요");
        }
        LocalDate queryStart = from.minusDays(config.lookbackDays());
        Set<String> seen = new HashSet<>();
        for (int page = 1; ; page++) {
            JsonNode body = body(http.get(url("/searchFestival2", "&eventStartDate=" + compact(queryStart)
                    + "&numOfRows=" + config.pageSize() + "&pageNo=" + page), "목록 " + page + "쪽"));
            JsonNode items = body.path("items").path("item");
            if (!items.isArray() || items.isEmpty()) break;

            for (JsonNode item : items) {
                String id = text(item, "contentid");
                LocalDate start = dates.parse(text(item, "eventstartdate"));
                LocalDate end = dates.parse(text(item, "eventenddate"));
                // 조회 기간과 겹치지 않는 행사는 대상이 아니다
                if (id == null || !seen.add(id) || (end != null && end.isBefore(from)) || (start != null && start.isAfter(to))) continue;
                if (context.isFresh(SourceType.TOURAPI, id)) {
                    context.skipped();
                    continue;
                }
                try {
                    context.emit(fetchOne(id, item, start, end));
                } catch (RuntimeException e) {
                    context.failed(SourceType.TOURAPI, id, e);
                }
            }
            if ((long) page * config.pageSize() >= body.path("totalCount").asLong(0)) break;
        }
    }

    private RawEvent fetchOne(String id, JsonNode list, LocalDate start, LocalDate end) {
        JsonNode common = first(body(http.get(url("/detailCommon2", "&contentId=" + id), "공통 상세 " + id)));
        JsonNode intro = first(body(http.get(url("/detailIntro2", "&contentId=" + id + "&contentTypeId=" + text(list, "contenttypeid")),
                "소개 상세 " + id)));

        String sponsor = join(text(intro, "sponsor1"), text(intro, "sponsor2"));
        String address = join(text(list, "addr1"), text(list, "addr2"));
        String image = firstNonNull(text(list, "firstimage2"), text(list, "firstimage"));
        return new RawEvent(
                SourceType.TOURAPI, id, text(list, "title"), text(common, "overview"), text(list, "lclsSystm3"),
                start, end, text(intro, "eventplace"), address, null, null,
                text(list, "lDongRegnCd"), text(list, "lDongSignguCd"),
                number(list, "mapy"), number(list, "mapx"),
                text(intro, "usetimefestival"), text(intro, "agelimit"), null, text(intro, "playtime"), null, sponsor,
                image, text(list, "cpyrhtDivCd"), homepage(text(common, "homepage")), List.of(),
                modifiedAt(text(list, "modifiedtime")), common.toString() + "\n" + intro);
    }

    /** 응답 JSON 의 response.body. 오류 응답이면 예외 */
    private JsonNode body(String text) {
        JsonNode root = json.readTree(text).path("response");
        String code = root.path("header").path("resultCode").asString("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("TourAPI 오류 응답: " + code + " " + root.path("header").path("resultMsg").asString(""));
        }
        return root.path("body");
    }

    private static JsonNode first(JsonNode body) {
        JsonNode items = body.path("items").path("item");
        return items.isArray() && !items.isEmpty() ? items.get(0) : json0();
    }

    private static JsonNode json0() {
        return JsonMapper.builder().build().createObjectNode();
    }

    /** homepage 는 `<a href="...">` 같은 HTML 로 오기도 한다. 주소만 뽑는다 */
    private static String homepage(String value) {
        if (value == null) return null;
        Matcher href = HREF.matcher(value);
        if (href.find()) return href.group(1);
        Matcher url = URL.matcher(value);
        return url.find() ? url.group() : null;
    }

    private OffsetDateTime modifiedAt(String text) {
        if (text == null) return null;
        try {
            return LocalDateTime.parse(text, MODIFIED_AT).atZone(zone).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asString("");
        return s.isBlank() ? null : s.trim();
    }

    private static Double number(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + ", " + b;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private String url(String path, String query) {
        return config.baseUrl() + path + "?serviceKey=" + encodeKey() + "&MobileOS=ETC&MobileApp=" + URLEncoder.encode(appName, StandardCharsets.UTF_8) + "&_type=json" + query;
    }

    private String encodeKey() {
        return apiKey.contains("%") ? apiKey : URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private static String compact(LocalDate date) {
        return date.toString().replace("-", "");
    }
}
