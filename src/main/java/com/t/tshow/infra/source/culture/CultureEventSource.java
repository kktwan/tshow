package com.t.tshow.infra.source.culture;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.service.normalize.DateParser;
import com.t.tshow.domain.ingest.source.IngestContext;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.util.Dates;
import com.t.tshow.global.util.Texts;
import com.t.tshow.global.util.Xml;
import com.t.tshow.infra.http.ApiClient;
import com.t.tshow.infra.http.ApiRequest;
import com.t.tshow.infra.http.RestApiClient;
import com.t.tshow.infra.source.AbstractEventSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.time.LocalDate;
import java.util.List;

/**
 * 한국문화정보원 한눈에보는문화정보조회서비스(공연·전시) 어댑터.
 * 기간별 목록(period2)으로 대상을 찾고, 작품마다 상세(detail2)에서 가격·설명·주소·링크를 가져온다.
 */
@Component
public class CultureEventSource extends AbstractEventSource {

    private final DateParser dates;

    @Autowired
    public CultureEventSource(IngestProperties properties, DateParser dates, @Value("${culture.api-key:}") String apiKey) {
        this(properties, dates, apiKey, new RestApiClient("CULTURE", properties.culture().policy()));
    }

    /** 테스트에서 가짜 응답을 넣기 위한 생성자 */
    CultureEventSource(IngestProperties properties, DateParser dates, String apiKey, ApiClient http) {
        super(SourceType.CULTURE, properties.culture(), http, apiKey, "serviceKey");
        this.dates = dates;
    }

    @Override
    public void fetch(LocalDate from, LocalDate to, IngestContext context) {
        requireApiKey();
        Delivery delivery = delivery(context);
        forEachWindow(from, to, (start, end) -> {
            for (int page = 1; ; page++) {
                List<Element> items = Xml.elements(Xml.parse(http.get(listRequest(start, end, page))), "item");
                for (Element item : items) {
                    String id = Xml.text(item, "seq");
                    delivery.deliver(id, () -> fetchOne(id, item));
                }
                if (items.size() < config.pageSize()) break;
            }
        });
    }

    private RawEvent fetchOne(String id, Element listItem) {
        String detailXml = http.get(request("/detail2").param("seq", id).label("상세 " + id));
        Element d = Xml.elements(Xml.parse(detailXml), "item").stream().findFirst().orElse(listItem);

        String thumbnail = Xml.text(listItem, "thumbnail");
        return new RawEvent(
                SourceType.CULTURE, id, Texts.firstOf(Xml.text(d, "title"), Xml.text(listItem, "title")),
                Xml.text(d, "contents1"), Xml.text(listItem, "realmName"),
                dates.parse(Texts.firstOf(Xml.text(d, "startDate"), Xml.text(listItem, "startDate"))),
                dates.parse(Texts.firstOf(Xml.text(d, "endDate"), Xml.text(listItem, "endDate"))),
                Texts.firstOf(Xml.text(d, "place"), Xml.text(listItem, "place")), Xml.text(d, "placeAddr"),
                Texts.firstOf(Xml.text(d, "area"), Xml.text(listItem, "area")),
                Texts.firstOf(Xml.text(d, "sigungu"), Xml.text(listItem, "sigungu")), null, null,
                firstNumber(d, listItem, "gpsY"), firstNumber(d, listItem, "gpsX"),
                Xml.text(d, "price"), null, null, null, null, null,
                Texts.firstOf(Xml.text(d, "imgUrl"), thumbnail), null, Xml.text(d, "url"), List.of(), null, detailXml);
    }

    private static Double firstNumber(Element primary, Element fallback, String tag) {
        Double v = Xml.number(primary, tag);
        return v != null ? v : Xml.number(fallback, tag);
    }

    private ApiRequest listRequest(LocalDate start, LocalDate end, int page) {
        return request("/period2")
                .param("PageNo", page).param("numOfrows", config.pageSize()).param("serviceTp", "A")
                .param("from", Dates.compact(start)).param("to", Dates.compact(end)).param("sortStdr", 1)
                .label("목록 " + start + "~" + end + " " + page + "쪽");
    }
}
