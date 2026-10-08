package com.t.tshow.domain.ingest.dto;

import com.t.tshow.domain.ingest.entity.SourceType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 소스 어댑터가 API 응답을 소스 형식 그대로의 값(이름·코드)으로 담아 넘기는 공통 모델.
 * 분류·지역 변환과 가격 판정은 이 단계 이후의 정규화가 소스를 모르고 처리한다.
 *
 * @param sourceCategory 소스의 분류 이름 또는 코드 (분류표로 표준 분류로 바뀐다)
 * @param sidoText       소스가 준 시도 이름 (없으면 null)
 * @param sigunguText    소스가 준 시군구 이름 (없으면 null)
 * @param sidoCode       소스가 준 시도 코드 (TourAPI 처럼 코드를 주는 경우)
 * @param sigunguCode    소스가 준 시군구 코드
 * @param imageLicense   이미지 이용 조건 (TourAPI 저작권 유형 등, 모르면 null)
 * @param infoUrl        소스가 주는 상세·홈페이지 링크 (예매 링크와 구분, 없으면 null)
 * @param raw            원본 응답 (문제 추적용)
 */
public record RawEvent(
        SourceType source,
        String sourceId,
        String title,
        String description,
        String sourceCategory,
        LocalDate startDate,
        LocalDate endDate,
        String venueName,
        String address,
        String sidoText,
        String sigunguText,
        String sidoCode,
        String sigunguCode,
        Double lat,
        Double lon,
        String priceText,
        String ageText,
        String runtimeText,
        String scheduleText,
        String castText,
        String hostText,
        String imageUrl,
        String imageLicense,
        String infoUrl,
        List<TicketLink> ticketLinks,
        OffsetDateTime sourceUpdatedAt,
        String raw) {
}
