package com.t.tshow.event;

import com.t.tshow.global.config.MergeProperties;
import com.t.tshow.ingest.SourceRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 같은 행사로 묶인 소스 레코드들을 하나의 {@link Event} 로 합친다.
 * 필드마다 소스 우선순위({@link MergeProperties#priorityOf})에 따라 값이 있는 첫 레코드의 값을 쓰고,
 * 관련 있는 값(제목과 정규화 제목, 장소 이름과 정규화 이름, 가격 문장과 가격 구분, 이미지와 이용 조건, 좌표 쌍)은 같은 레코드에서 함께 가져온다.
 */
@Component
public class EventMerger {

    private final MergeProperties properties;

    public EventMerger(MergeProperties properties) {
        this.properties = properties;
    }

    /** id 는 비어 있다 (계획 단계에서 정한다) */
    public Event merge(List<SourceRecord> members) {
        SourceRecord title = first(members, "title", r -> has(r.title()));
        SourceRecord category = first(members, "category", r -> has(r.category()));
        SourceRecord dates = first(members, "dates", r -> r.startDate() != null);
        SourceRecord venue = first(members, "venue", r -> has(r.venueName()));
        SourceRecord address = first(members, "address", r -> has(r.address()));
        SourceRecord region = first(members, "region", r -> has(r.sidoCode()));
        SourceRecord location = first(members, "location", r -> r.lat() != null && r.lon() != null);
        SourceRecord price = first(members, "price", r -> has(r.priceText()) && !"UNKNOWN".equals(r.priceType()));
        if (price == null) price = first(members, "price", r -> has(r.priceText()));
        SourceRecord image = first(members, "image", r -> has(r.imageUrl()));

        SourceRecord anyTitle = title != null ? title : members.get(0);
        SourceRecord anyCategory = category != null ? category : members.get(0);
        String description = value(members, "description", SourceRecord::description);

        String sigungu = null;
        if (region != null) {
            sigungu = region.sigunguCode();
            if (sigungu == null) {
                // 같은 시도로 확인된 다른 레코드에 시군구가 있으면 쓴다
                String sido = region.sidoCode();
                sigungu = members.stream().filter(r -> sido.equals(r.sidoCode()) && r.sigunguCode() != null)
                        .map(SourceRecord::sigunguCode).findFirst().orElse(null);
            }
        }

        Event unhashed = new Event(null, anyCategory.kind(), anyCategory.category(),
                anyTitle.title(), anyTitle.titleNorm(), description,
                dates == null ? null : dates.startDate(), dates == null ? null : dates.endDate(),
                venue == null ? null : venue.venueName(), venue == null ? null : venue.venueNameNorm(),
                address == null ? null : address.address(),
                region == null ? null : region.sidoCode(), sigungu,
                location == null ? null : location.lat(), location == null ? null : location.lon(),
                price == null ? "UNKNOWN" : price.priceType(), price == null ? null : price.priceText(),
                value(members, "age", SourceRecord::ageText), value(members, "runtime", SourceRecord::runtimeText),
                value(members, "schedule", SourceRecord::scheduleText), value(members, "cast", SourceRecord::castText),
                value(members, "host", SourceRecord::hostText),
                image == null ? null : image.imageUrl(), image == null ? null : image.imageLicense(),
                value(members, "info-url", SourceRecord::infoUrl), value(members, "ticket-links", SourceRecord::ticketLinksJson),
                members.size(), description != null, null);
        return withHash(unhashed);
    }

    /** 필드의 소스 우선순위 순서로 정렬한 뒤 조건을 만족하는 첫 레코드. 없으면 null */
    private SourceRecord first(List<SourceRecord> members, String field, Predicate<SourceRecord> condition) {
        List<String> order = properties.priorityOf(field);
        return members.stream()
                .sorted(Comparator.comparingInt((SourceRecord r) -> rank(order, r.source())).thenComparing(r -> r.id() == null ? 0L : r.id()))
                .filter(condition).findFirst().orElse(null);
    }

    private <T> T value(List<SourceRecord> members, String field, Function<SourceRecord, T> getter) {
        SourceRecord r = first(members, field, x -> has(getter.apply(x)));
        return r == null ? null : getter.apply(r);
    }

    private static int rank(List<String> order, String source) {
        int i = order.indexOf(source);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static boolean has(Object value) {
        return value != null && !(value instanceof String s && s.isBlank());
    }

    private Event withHash(Event e) {
        String joined = String.join("\u0001", java.util.stream.Stream.of(
                        e.kind(), e.category(), e.title(), e.description(), str(e.startDate()), str(e.endDate()),
                        e.venueName(), e.address(), e.sidoCode(), e.sigunguCode(), str(e.lat()), str(e.lon()),
                        e.priceType(), e.priceText(), e.ageText(), e.runtimeText(), e.scheduleText(), e.castText(),
                        e.hostText(), e.imageUrl(), e.imageLicense(), e.infoUrl(), e.ticketLinksJson(), str(e.sourceCount()))
                .map(EventMerger::nullToEmpty).toList());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return new Event(e.id(), e.kind(), e.category(), e.title(), e.titleNorm(), e.description(), e.startDate(), e.endDate(),
                    e.venueName(), e.venueNameNorm(), e.address(), e.sidoCode(), e.sigunguCode(), e.lat(), e.lon(), e.priceType(),
                    e.priceText(), e.ageText(), e.runtimeText(), e.scheduleText(), e.castText(), e.hostText(), e.imageUrl(),
                    e.imageLicense(), e.infoUrl(), e.ticketLinksJson(), e.sourceCount(), e.hasDescription(),
                    HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String str(Object o) {
        return Objects.toString(o, null);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
