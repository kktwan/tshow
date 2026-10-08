package com.t.tshow.domain.event.dto;

import java.time.LocalDate;

/**
 * 행사 목록 조건. 비어 있는 조건은 걸지 않는다.
 *
 * @param category  표준 분류 id (categories.yml)
 * @param sido      시도 코드
 * @param priceType FREE / PAID / UNKNOWN
 * @param from      이 날짜 이후에도 열리는 행사
 * @param to        이 날짜 이전에 시작하는 행사
 * @param keyword   제목에 들어 있는 글자
 */
public record EventSearchCondition(String category, String sido, String priceType, LocalDate from, LocalDate to, String keyword) {
}
