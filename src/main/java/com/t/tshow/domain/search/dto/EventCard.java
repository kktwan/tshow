package com.t.tshow.domain.search.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 검색 결과의 행사 한 건 (카드에 보일 만큼의 정보).
 *
 * @param period         기간 글 (10.28 (수) – 12.31 (목))
 * @param status         진행 상태 글 (D-3, 진행 중, 오늘 종료…)
 * @param statusTone     상태 색 구분 (soon, live, ended)
 * @param distanceMeters 내 주변 검색일 때 내 위치에서의 거리 (아니면 null)
 * @param score          의미 검색의 유사도 (조건만으로 찾았으면 null). 화면에는 보이지 않고 점수 하한을 정할 때 본다
 * @param imageProtected 변경금지 이미지(공공누리 제3·4유형)라서 자르지 않고 원본 그대로 보여야 하는지
 */
public record EventCard(
        UUID id,
        String title,
        String kindName,
        String categoryName,
        String imageUrl,
        LocalDate startDate,
        LocalDate endDate,
        String period,
        String status,
        String statusTone,
        String venueName,
        String regionName,
        String priceType,
        Integer distanceMeters,
        Double score,
        boolean imageProtected) {

    /** 거리를 1.2km / 350m 처럼. 거리를 모르면 null */
    public String distanceLabel() {
        if (distanceMeters == null) return null;
        return distanceMeters >= 1000 ? String.format("%.1fkm", distanceMeters / 1000.0) : distanceMeters + "m";
    }
}
