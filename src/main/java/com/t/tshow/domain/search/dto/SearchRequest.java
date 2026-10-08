package com.t.tshow.domain.search.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 검색 요청. 화면의 선택(분류·지역·날짜…)은 검색어 속 표현보다 우선한다. 비어 있는 값은 걸지 않는다.
 *
 * @param q        검색어 (예: "이번 주말 서울 무료 전시", "아이와 갈 만한 곳")
 * @param kind     종류(PERFORMANCE, EXHIBITION, FESTIVAL)
 * @param category 표준 분류 id
 * @param sido     시도 코드
 * @param sigungu  시군구 코드
 * @param when     날짜 규칙 이름 (today, tomorrow, weekend, next-weekend, this-week, this-month, next-month)
 * @param date     특정 날짜 하나 (when 보다 우선)
 * @param free     true 면 무료만
 * @param lat      내 위치 위도 (lon, radiusKm 와 함께 쓰면 "내 주변")
 * @param lon      내 위치 경도
 * @param radiusKm 반경(km). 없으면 기본값
 * @param page     0부터 시작하는 쪽 번호
 * @param size     한 쪽 크기. 없으면 기본값
 */
public record SearchRequest(
        String q,
        String kind,
        String category,
        String sido,
        String sigungu,
        String when,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Boolean free,
        Double lat,
        Double lon,
        Integer radiusKm,
        Integer page,
        Integer size) {
}
