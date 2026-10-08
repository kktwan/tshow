package com.t.tshow.domain.event.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 행사 목록 조건. 비어 있는(null, 빈 목록) 조건은 걸지 않는다.
 *
 * @param kinds      종류(PERFORMANCE, EXHIBITION, FESTIVAL) 중 하나라도
 * @param categories 표준 분류 id 중 하나라도 (categories.yml)
 * @param sido       시도 코드
 * @param sigungu    시군구 코드
 * @param priceType  FREE / PAID / UNKNOWN
 * @param from       이 날짜 이후에도 열리는 행사 (종료일이 이 날짜 이후)
 * @param to         이 날짜 이전에 시작하는 행사
 * @param keyword    제목에 들어 있는 글자
 * @param near       이 지점 반경 안의 행사 (먼저 사각형으로 거르고, 정확한 거리는 호출한 쪽이 계산한다)
 */
public record EventSearchCondition(List<String> kinds, List<String> categories, String sido, String sigungu, String priceType,
                                   LocalDate from, LocalDate to, String keyword, Near near) {

    /** 중심 좌표와 반경(m) */
    public record Near(double lat, double lon, double radiusMeters) {
    }
}
