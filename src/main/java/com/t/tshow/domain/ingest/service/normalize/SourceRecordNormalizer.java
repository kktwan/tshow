package com.t.tshow.domain.ingest.service.normalize;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.dto.TicketLink;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.util.Json;
import com.t.tshow.global.util.Texts;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

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
                .address(Texts.blankToNull(raw.address()))
                .sidoCode(region.sidoCode()).sigunguCode(region.sigunguCode()).lat(raw.lat()).lon(raw.lon())
                .priceType(prices.classify(raw.priceText()).name()).priceText(Texts.blankToNull(raw.priceText()))
                .ageText(Texts.blankToNull(raw.ageText())).runtimeText(Texts.blankToNull(raw.runtimeText()))
                .scheduleText(Texts.blankToNull(raw.scheduleText()))
                .castText(Texts.blankToNull(raw.castText())).hostText(Texts.blankToNull(raw.hostText()))
                .imageUrl(image(raw.imageUrl())).imageLicense(Texts.blankToNull(raw.imageLicense()))
                .infoUrl(Texts.blankToNull(raw.infoUrl()))
                .ticketLinksJson(ticketLinksJson(raw.ticketLinks()))
                .sourceUpdatedAt(raw.sourceUpdatedAt()).fetchedAt(fetchedAt).raw(raw.raw())
                .build();
    }

    /**
     * 소스가 글자를 HTML 엔티티로 준 경우(&amp; &lt; &#39; 등, 두 번 감싸져 오기도 한다)를 원래 글자로 바꾼다.
     * 제목·장소·본문 같은 사람이 읽는 글에만 적용하고 주소(URL)나 원본 응답은 건드리지 않는다.
     */
    private static RawEvent unescaped(RawEvent r) {
        return new RawEvent(r.source(), r.sourceId(), unescape(r.title()), unescape(r.description()), r.sourceCategory(),
                r.startDate(), r.endDate(), unescape(r.venueName()), unescape(r.address()), unescape(r.sidoText()), unescape(r.sigunguText()),
                r.sidoCode(), r.sigunguCode(), r.lat(), r.lon(), unescape(r.priceText()), unescape(r.ageText()),
                unescape(r.runtimeText()), unescape(r.scheduleText()), unescape(r.castText()), unescape(r.hostText()),
                r.imageUrl(), r.imageLicense(), r.infoUrl(), r.ticketLinks(), r.sourceUpdatedAt(), r.raw());
    }

    /** 더 바뀌지 않을 때까지 (최대 3번) 해제한다 */
    static String unescape(String text) {
        if (text == null) return null;
        String current = text;
        for (int i = 0; i < 3; i++) {
            String next = HtmlUtils.htmlUnescape(current);
            if (next.equals(current)) break;
            current = next;
        }
        return current;
    }

    private String image(String url) {
        String u = Texts.blankToNull(url);
        if (u != null && upgradeImageHttps && u.startsWith("http://")) {
            return "https://" + u.substring("http://".length());
        }
        return u;
    }

    private static String ticketLinksJson(List<TicketLink> links) {
        if (links == null || links.isEmpty()) return null;
        List<Map<String, String>> list = links.stream().map(l -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", l.name());
            m.put("url", l.url());
            return m;
        }).toList();
        return Json.write(list);
    }
}
