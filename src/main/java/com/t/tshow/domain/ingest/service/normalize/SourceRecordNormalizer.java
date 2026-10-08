package com.t.tshow.domain.ingest.service.normalize;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.dto.TicketLink;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.util.Html;
import com.t.tshow.global.util.Json;
import com.t.tshow.global.util.Texts;
import com.t.tshow.global.util.Urls;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 소스 형식의 {@link RawEvent} 를 표준 분류·지역·가격·비교용 문자열을 갖춘 {@link SourceRecord} 로 바꾼다. 소스를 가리지 않는다. */
@Component
public class SourceRecordNormalizer {

    private final CategoryResolver categories;
    private final RegionResolver regions;
    private final PriceClassifier prices;
    private final TextNormalizer texts;
    private final boolean upgradeImageHttps;

    public SourceRecordNormalizer(CategoryResolver categories, RegionResolver regions, PriceClassifier prices,
                                  TextNormalizer texts, IngestProperties properties) {
        this.categories = categories;
        this.regions = regions;
        this.prices = prices;
        this.texts = texts;
        this.upgradeImageHttps = properties.upgradeImageHttps();
    }

    public SourceRecord normalize(RawEvent source, Instant fetchedAt, NormalizationReport report) {
        RawEvent raw = unescaped(source);
        CategoryResolver.Category category = categories.resolve(raw.source(), raw.sourceCategory(), report);
        RegionResolver.Region region = regions.resolve(raw.sidoText(), raw.sigunguText(), raw.address(),
                raw.sidoCode(), raw.sigunguCode(), report);
        return SourceRecord.builder()
                .source(raw.source()).sourceId(raw.sourceId())
                .kind(category.kind()).category(category.id()).sourceCategory(raw.sourceCategory())
                .title(raw.title()).titleNorm(texts.title(raw.title())).description(Texts.blankToNull(raw.description()))
                .startDate(raw.startDate()).endDate(raw.endDate())
                .venueName(Texts.blankToNull(raw.venueName())).venueNameNorm(texts.venue(raw.venueName()))
                .address(regions.cleanAddress(Texts.blankToNull(raw.address())))
                .sidoCode(region.sidoCode()).sigunguCode(region.sigunguCode()).lat(raw.lat()).lon(raw.lon())
                .priceType(prices.classify(raw.priceText()).name()).priceText(Texts.blankToNull(raw.priceText()))
                .ageText(Texts.blankToNull(raw.ageText())).runtimeText(Texts.blankToNull(raw.runtimeText()))
                .scheduleText(Texts.blankToNull(raw.scheduleText()))
                .castText(Texts.blankToNull(raw.castText())).hostText(Texts.blankToNull(raw.hostText()))
                .imageUrl(image(raw.imageUrl())).imageLicense(Texts.blankToNull(raw.imageLicense()))
                .infoUrl(Urls.safeHttp(raw.infoUrl())).placeUrl(Urls.safeHttp(raw.placeUrl()))
                .phone(Texts.blankToNull(raw.phone()))
                .ticketLinksJson(ticketLinksJson(raw.ticketLinks()))
                .sourceUpdatedAt(raw.sourceUpdatedAt()).fetchedAt(fetchedAt).raw(raw.raw())
                .build();
    }

    /**
     * 소스가 글자를 HTML 엔티티로 준 경우(&amp; &lt; &#39; 등, 두 번 감싸져 오기도 한다)를 원래 글자로 바꾼다.
     * 제목·장소·본문 같은 사람이 읽는 글에만 적용하고 주소(URL)나 원본 응답은 건드리지 않는다.
     */
    private static RawEvent unescaped(RawEvent r) {
        return new RawEvent(r.source(), r.sourceId(), unescape(r.title()), text(r.description()), r.sourceCategory(),
                r.startDate(), r.endDate(), unescape(r.venueName()), unescape(r.address()), unescape(r.sidoText()), unescape(r.sigunguText()),
                r.sidoCode(), r.sigunguCode(), r.lat(), r.lon(), text(r.priceText()), text(r.ageText()),
                text(r.runtimeText()), text(r.scheduleText()), text(r.castText()), text(r.hostText()),
                r.imageUrl(), r.imageLicense(), r.infoUrl(), r.placeUrl(), text(r.phone()), r.ticketLinks(), r.sourceUpdatedAt(), r.raw());
    }

    /** 본문류 글: 엔티티를 풀고 HTML 태그를 걷어 보통 글로 만든다 (소개글이 HTML 로 오는 소스가 있다). 제목·장소·주소에는 쓰지 않는다 */
    private static String text(String value) {
        return Html.toPlainText(value);
    }

    /** 제목·장소·주소 같은 짧은 글의 엔티티를 푼다 (태그는 걷지 않는다 — <다담> 같은 글자가 제목에 쓰일 수 있다) */
    static String unescape(String text) {
        return Html.unescape(text);
    }

    private String image(String url) {
        String u = Urls.safeHttp(url);
        if (u != null && upgradeImageHttps && u.startsWith("http://")) {
            return "https://" + u.substring("http://".length());
        }
        return u;
    }

    private static String ticketLinksJson(List<TicketLink> links) {
        if (links == null || links.isEmpty()) return null;
        // 화면의 예매 버튼이 되므로 http(s) 주소만 남긴다 (javascript: 같은 값과 깨진 엔티티를 걸러낸다)
        List<Map<String, String>> list = links.stream().filter(l -> Urls.safeHttp(l.url()) != null).map(l -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", l.name());
            m.put("url", Urls.safeHttp(l.url()));
            return m;
        }).toList();
        return list.isEmpty() ? null : Json.write(list);
    }
}
