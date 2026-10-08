package com.t.tshow.ingest.normalize;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.ingest.Json;
import com.t.tshow.ingest.SourceRecord;
import com.t.tshow.ingest.source.RawEvent;
import com.t.tshow.ingest.source.TicketLink;
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

    public SourceRecord normalize(RawEvent raw, Instant fetchedAt, NormalizationReport report) {
        CategoryResolver.Category category = categories.resolve(raw.source(), raw.sourceCategory(), report);
        RegionResolver.Region region = regions.resolve(raw.sidoText(), raw.sigunguText(), raw.address(),
                raw.sidoCode(), raw.sigunguCode(), report);
        return new SourceRecord(
                raw.source().name(), raw.sourceId(), category.kind(), category.id(), raw.sourceCategory(),
                raw.title(), texts.title(raw.title()), blankToNull(raw.description()),
                raw.startDate(), raw.endDate(),
                blankToNull(raw.venueName()), texts.venue(raw.venueName()), blankToNull(raw.address()),
                region.sidoCode(), region.sigunguCode(), raw.lat(), raw.lon(),
                prices.classify(raw.priceText()).name(), blankToNull(raw.priceText()),
                blankToNull(raw.ageText()), blankToNull(raw.runtimeText()), blankToNull(raw.scheduleText()),
                blankToNull(raw.castText()), blankToNull(raw.hostText()),
                image(raw.imageUrl()), blankToNull(raw.imageLicense()), blankToNull(raw.infoUrl()),
                ticketLinksJson(raw.ticketLinks()),
                raw.sourceUpdatedAt(), fetchedAt, raw.raw());
    }

    private String image(String url) {
        String u = blankToNull(url);
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
