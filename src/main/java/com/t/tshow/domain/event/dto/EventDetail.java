package com.t.tshow.domain.event.dto;

import com.t.tshow.domain.ingest.dto.TicketLink;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String imageCredit,
        boolean imageProtected,
        String infoUrl,
        String placeUrl,
        String phone,
        List<TicketLink> ticketLinks,
        List<String> sources) {

    /** 전화번호(02-123-4567, 1588-1234 등)와 그 앞뒤 글 (국립중앙박물관 02-2077-9000 처럼 이름이 붙어 올 수 있다) */
    private static final Pattern TEL = Pattern.compile("(0\\d{1,2}[-. )]?\\d{3,4}[-. ]?\\d{4}|1[5-9]\\d{2}[-. ]?\\d{4})");

    /** 눌러서 전화를 걸 수 있는 주소(tel:0212345678). 문의 글에서 전화번호를 찾지 못하면 null */
    public String telHref() {
        if (phone == null) return null;
        Matcher m = TEL.matcher(phone);
        return m.find() ? "tel:" + m.group().replaceAll("[^0-9]", "") : null;
    }
}
