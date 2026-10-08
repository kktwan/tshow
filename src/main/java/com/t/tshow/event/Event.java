package com.t.tshow.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 병합된 행사 하나 (event 테이블의 한 행). 여러 소스의 같은 행사를 합친 결과이며 검색·색인은 이것을 쓴다.
 * id 는 저장 전 계획 단계에서 정해진다. dataHash 는 내용 필드의 해시로, 바뀐 행사만 갱신하고 색인 변경분을 가리는 데 쓴다.
 */
public record Event(
        UUID id,
        String kind,
        String category,
        String title,
        String titleNorm,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String venueName,
        String venueNameNorm,
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
        String imageLicense,
        String infoUrl,
        String ticketLinksJson,
        int sourceCount,
        boolean hasDescription,
        String dataHash) {

    public Event withId(UUID newId) {
        return new Event(newId, kind, category, title, titleNorm, description, startDate, endDate, venueName, venueNameNorm,
                address, sidoCode, sigunguCode, lat, lon, priceType, priceText, ageText, runtimeText, scheduleText, castText,
                hostText, imageUrl, imageLicense, infoUrl, ticketLinksJson, sourceCount, hasDescription, dataHash);
    }
}
