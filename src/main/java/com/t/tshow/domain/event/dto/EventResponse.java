package com.t.tshow.domain.event.dto;

import com.t.tshow.domain.event.entity.Event;

import java.time.LocalDate;
import java.util.UUID;

/** API 가 돌려주는 행사 정보. 해시·색인 상태 같은 내부 값은 담지 않는다 */
public record EventResponse(
        UUID id,
        String kind,
        String category,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String venueName,
        String address,
        String sidoCode,
        String sigunguCode,
        Double lat,
        Double lon,
        String priceType,
        String priceText,
        String ageText,
        String runtimeText,
        String scheduleText,
        String castText,
        String hostText,
        String imageUrl,
        String infoUrl,
        String ticketLinks) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getKind(), e.getCategory(), e.getTitle(), e.getDescription(),
                e.getStartDate(), e.getEndDate(), e.getVenueName(), e.getAddress(), e.getSidoCode(), e.getSigunguCode(),
                e.getLat(), e.getLon(), e.getPriceType(), e.getPriceText(), e.getAgeText(), e.getRuntimeText(),
                e.getScheduleText(), e.getCastText(), e.getHostText(), e.getImageUrl(), e.getInfoUrl(), e.getTicketLinksJson());
    }
}
