package com.t.tshow.infra.source.tourapi;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.service.normalize.DateParser;
import com.t.tshow.domain.ingest.source.IngestContext;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.util.Dates;
import com.t.tshow.global.util.Texts;
import com.t.tshow.infra.http.ApiClient;
import com.t.tshow.infra.http.ApiRequest;
import com.t.tshow.infra.http.RestApiClient;
import com.t.tshow.infra.source.AbstractEventSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국관광공사 TourAPI(국문 관광정보서비스) 행사 어댑터.
 * 행사 목록(searchFestival2)은 시작일 기준으로만 조회되므로 lookback-days 만큼 과거부터 가져와 조회 기간과 겹치는 것만 남기고,
 * 작품마다 공통 상세(detailCommon2: 소개글·홈페이지)와 소개 상세(detailIntro2: 주최·시간·요금·연령)를 가져온다.
 */
@Component
public class TourApiEventSource extends AbstractEventSource {

    private static final DateTimeFormatter MODIFIED_AT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DateParser dates;
    private final ZoneId zone;
    private final String appName;

    @Autowired
    public TourApiEventSource(IngestProperties properties, DateParser dates, @Value("${tourapi.api-key:}") String apiKey) {
        this(properties, dates, apiKey, new RestApiClient("TOURAPI", properties.tourapi().policy()));
    }

    /** 테스트에서 가짜 응답을 넣기 위한 생성자 */
    TourApiEventSource(IngestProperties properties, DateParser dates, String apiKey, ApiClient http) {
        super(SourceType.TOURAPI, properties.tourapi(), http, apiKey, "serviceKey");
        this.dates = dates;
        this.zone = ZoneId.of(properties.zone());
        this.appName = properties.appName();
    }

    @Override
    public void fetch(LocalDate from, LocalDate to, IngestContext context) {
        requireApiKey();
        LocalDate queryStart = from.minusDays(config.lookbackDays());
        Delivery delivery = delivery(context);
        for (int page = 1; ; page++) {
            JsonNode body = body(http.get(listRequest(queryStart, page)));
            JsonNode items = body.path("items").path("item");
            if (!items.isArray() || items.isEmpty()) break;

            for (JsonNode item : items) {
                LocalDate start = dates.parse(text(item, "eventstartdate"));
                LocalDate end = dates.parse(text(item, "eventenddate"));
                // 조회 기간과 겹치지 않는 행사는 대상이 아니다
                if ((end != null && end.isBefore(from)) || (start != null && start.isAfter(to))) continue;
                String id = text(item, "contentid");
                delivery.deliver(id, () -> fetchOne(id, item, start, end));
            }
            if ((long) page * config.pageSize() >= body.path("totalCount").asLong(0)) break;
        }
    }

    private RawEvent fetchOne(String id, JsonNode list, LocalDate start, LocalDate end) {
        JsonNode common = first(body(http.get(detailRequest("/detailCommon2", id).label("공통 상세 " + id))));
        JsonNode intro = first(body(http.get(detailRequest("/detailIntro2", id)
                .param("contentTypeId", text(list, "contenttypeid")).label("소개 상세 " + id))));

        String sponsor = join(text(intro, "sponsor1"), text(intro, "sponsor2"));
        String address = join(text(list, "addr1"), text(list, "addr2"));
        String image = Texts.firstOf(text(list, "firstimage2"), text(list, "firstimage"));
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
        JsonNode root = JSON.readTree(text).path("response");
        String code = root.path("header").path("resultCode").asString("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("TourAPI 오류 응답: " + code + " " + root.path("header").path("resultMsg").asString(""));
        }
        return root.path("body");
    }

    private static JsonNode first(JsonNode body) {
        JsonNode items = body.path("items").path("item");
        return items.isArray() && !items.isEmpty() ? items.get(0) : JSON.createObjectNode();
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
        return Texts.blankToNull(v.asString(""));
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

    /** TourAPI 공통 파라미터(앱 이름·OS·JSON 응답)를 붙인 요청 */
    private ApiRequest tourRequest(String path) {
        return request(path).param("MobileOS", "ETC").param("MobileApp", appName).param("_type", "json");
    }

    private ApiRequest listRequest(LocalDate queryStart, int page) {
        return tourRequest("/searchFestival2")
                .param("eventStartDate", Dates.compact(queryStart))
                .param("numOfRows", config.pageSize()).param("pageNo", page)
                .label("목록 " + page + "쪽");
    }

    private ApiRequest detailRequest(String path, String contentId) {
        return tourRequest(path).param("contentId", contentId);
    }
}
