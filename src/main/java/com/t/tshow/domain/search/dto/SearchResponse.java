package com.t.tshow.domain.search.dto;

import java.util.List;

/**
 * 검색 결과 한 쪽.
 *
 * @param query        해석한 검색어 (조건을 뺀 의미 검색 문장, 없으면 빈 문자열)
 * @param interpreted  검색어에서 뽑아 적용한 조건 이름들 (서울, 이번 주말, 무료…) — 화면에 "이렇게 이해했어요" 로 보인다
 * @param semantic     의미 검색을 했는지 (false 면 조건만으로 찾았다)
 * @param degraded     의미 검색을 못 해서 제목 글자 검색으로 대신했는지
 * @param page         0부터 시작하는 쪽 번호
 * @param totalItems   조건에 맞는 전체 건수 (의미 검색은 관련 있다고 본 후보 수)
 */
public record SearchResponse(
        String query,
        List<String> interpreted,
        boolean semantic,
        boolean degraded,
        List<EventCard> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
