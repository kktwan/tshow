package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.global.config.MergeProperties;
import com.t.tshow.global.util.Hashes;
import com.t.tshow.global.util.Html;
import com.t.tshow.global.util.Texts;
import com.t.tshow.global.util.Urls;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
        SourceRecord title = first(members, "title", r -> Texts.has(r.getTitle()));
        SourceRecord category = first(members, "category", r -> Texts.has(r.getCategory()));
        SourceRecord dates = first(members, "dates", r -> r.getStartDate() != null);
        SourceRecord venue = first(members, "venue", r -> Texts.has(r.getVenueName()));
        SourceRecord address = first(members, "address", r -> Texts.has(r.getAddress()));
        SourceRecord region = first(members, "region", r -> Texts.has(r.getSidoCode()));
        SourceRecord location = first(members, "location", r -> r.getLat() != null && r.getLon() != null);
        SourceRecord price = first(members, "price", r -> Texts.has(r.getPriceText()) && !"UNKNOWN".equals(r.getPriceType()));
        if (price == null) price = first(members, "price", r -> Texts.has(r.getPriceText()));
        SourceRecord image = first(members, "image", r -> Texts.has(r.getImageUrl()));

        SourceRecord anyTitle = title != null ? title : members.get(0);
        SourceRecord anyCategory = category != null ? category : members.get(0);
        String description = text(value(members, "description", SourceRecord::getDescription));

        String sigungu = null;
        if (region != null) {
            sigungu = region.getSigunguCode();
            if (sigungu == null) {
                // 같은 시도로 확인된 다른 레코드에 시군구가 있으면 쓴다
                String sido = region.getSidoCode();
                sigungu = members.stream().filter(r -> sido.equals(r.getSidoCode()) && r.getSigunguCode() != null)
                        .map(SourceRecord::getSigunguCode).findFirst().orElse(null);
            }
        }

        Event unhashed = Event.builder()
                .kind(anyCategory.getKind()).category(anyCategory.getCategory())
                .title(Html.unescape(anyTitle.getTitle())).titleNorm(anyTitle.getTitleNorm()).description(description)
                .startDate(dates == null ? null : dates.getStartDate()).endDate(dates == null ? null : dates.getEndDate())
                .venueName(venue == null ? null : Html.unescape(venue.getVenueName())).venueNameNorm(venue == null ? null : venue.getVenueNameNorm())
                .address(address == null ? null : Html.unescape(address.getAddress()))
                .sidoCode(region == null ? null : region.getSidoCode()).sigunguCode(sigungu)
                .lat(location == null ? null : location.getLat()).lon(location == null ? null : location.getLon())
                .priceType(price == null ? "UNKNOWN" : price.getPriceType()).priceText(price == null ? null : text(price.getPriceText()))
                .ageText(text(value(members, "age", SourceRecord::getAgeText)))
                .runtimeText(text(value(members, "runtime", SourceRecord::getRuntimeText)))
                .scheduleText(text(value(members, "schedule", SourceRecord::getScheduleText)))
                .castText(text(value(members, "cast", SourceRecord::getCastText)))
                .hostText(text(value(members, "host", SourceRecord::getHostText)))
                .imageUrl(image == null ? null : image.getImageUrl()).imageLicense(image == null ? null : image.getImageLicense())
                .infoUrl(Urls.safeHttp(value(members, "info-url", SourceRecord::getInfoUrl)))
                .placeUrl(Urls.safeHttp(value(members, "place-url", SourceRecord::getPlaceUrl)))
                .phone(text(value(members, "phone", SourceRecord::getPhone)))
                .ticketLinksJson(value(members, "ticket-links", SourceRecord::getTicketLinksJson))
                .sourceCount(members.size()).hasDescription(description != null)
                .build();
        return unhashed.toBuilder().dataHash(hashOf(unhashed)).build();
    }

    /**
     * 본문류 글을 화면에 보일 보통 글로 다시 정리한다. 수집할 때 이미 정리하지만, 정리 규칙이 늘기 전에 저장된 레코드나
     * 소스를 다시 받기 전의 레코드도 병합 결과에서는 깨끗하게 하려고 한 번 더 적용한다(같은 값에 다시 적용해도 같다).
     */
    private static String text(String value) {
        return Texts.blankToNull(Html.toPlainText(value));
    }

    /** 필드의 소스 우선순위 순서로 정렬한 뒤 조건을 만족하는 첫 레코드. 없으면 null */
    private SourceRecord first(List<SourceRecord> members, String field, Predicate<SourceRecord> condition) {
        List<String> order = properties.priorityOf(field);
        return members.stream()
                .sorted(Comparator.comparingInt((SourceRecord r) -> rank(order, r.getSource().name()))
                        .thenComparing(r -> r.getId() == null ? 0L : r.getId()))
                .filter(condition).findFirst().orElse(null);
    }

    private <T> T value(List<SourceRecord> members, String field, Function<SourceRecord, T> getter) {
        SourceRecord r = first(members, field, x -> Texts.has(getter.apply(x)));
        return r == null ? null : getter.apply(r);
    }

    private static int rank(List<String> order, String source) {
        int i = order.indexOf(source);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** 병합 결과의 내용 필드를 이어 붙여 해시한다. 바뀐 행사만 갱신하고 색인 변경분을 가리는 데 쓴다 */
    private static String hashOf(Event e) {
        String base = String.join("\u0001", Stream.of(
                        e.getKind(), e.getCategory(), e.getTitle(), e.getDescription(), str(e.getStartDate()), str(e.getEndDate()),
                        e.getVenueName(), e.getAddress(), e.getSidoCode(), e.getSigunguCode(), str(e.getLat()), str(e.getLon()),
                        e.getPriceType(), e.getPriceText(), e.getAgeText(), e.getRuntimeText(), e.getScheduleText(), e.getCastText(),
                        e.getHostText(), e.getImageUrl(), e.getImageLicense(), e.getInfoUrl(), e.getTicketLinksJson(),
                        str(e.getSourceCount()))
                .map(Texts::nullToEmpty).toList());
        // 나중에 추가한 값은 있을 때만 해시에 넣는다 (값이 없는 기존 행사의 해시가 바뀌어 전부 갱신되지 않게)
        String extra = (e.getPlaceUrl() == null ? "" : "\u0002place=" + e.getPlaceUrl())
                + (e.getPhone() == null ? "" : "\u0002phone=" + e.getPhone());
        return Hashes.sha256(base + extra);
    }

    private static String str(Object o) {
        return Objects.toString(o, null);
    }
}
