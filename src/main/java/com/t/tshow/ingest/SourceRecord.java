package com.t.tshow.ingest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 소스별로 정규화한 한 건 (source_record 테이블의 한 행). 분류·지역·날짜·가격이 표준 값으로 바뀌어 있다.
 * 중복 제거(병합)는 이 레코드들을 읽어 별도 단계에서 한다.
 */
public record SourceRecord(
        String source,
        String sourceId,
        String kind,
        String category,
        String sourceCategory,
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
        OffsetDateTime sourceUpdatedAt,
        Instant fetchedAt,
        String raw) {
}
