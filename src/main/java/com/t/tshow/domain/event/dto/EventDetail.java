package com.t.tshow.domain.event.dto;

import com.t.tshow.domain.ingest.dto.TicketLink;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 행사 상세. 화면과 API 가 같이 쓴다. 해시·색인 상태 같은 내부 값은 담지 않는다 */
public record EventDetail(
        UUID id,
        String kind,
        String kindName,
        String category,
        String categoryName,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String period,
        String status,
        String statusTone,
        String venueName,
        String address,
        String regionName,
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
        List<TicketLink> ticketLinks,
        List<String> sources) {
}
