package com.t.tshow.domain.search.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 검색어와 화면 선택을 해석한 결과: 의미 검색에 쓸 문장과, 반드시 지켜야 하는 조건들.
 * 조건은 검색 전에 먼저 걸리고(사전 필터), 문장은 그 안에서 의미가 비슷한 순서를 정한다.
 *
 * @param text      의미 검색에 쓸 문장 (조건 표현과 군더더기를 뺀 것). 비어 있으면 조건만으로 찾는다
 * @param kinds     종류 조건
 * @param categories 분류 조건
 * @param free      true 면 무료만
 * @param from      이 날짜 이후에도 열리는 행사 (항상 오늘 이후)
 * @param to        이 날짜 이전에 시작하는 행사 (없으면 제한 없음)
 * @param dateKey   날짜 조건을 만든 규칙 이름 (화면 버튼 표시용, 없을 수 있음)
 * @param near      내 주변 조건
 */
public record SearchQuery(
        String text,
        List<String> kinds,
        List<String> categories,
        String sido,
        String sigungu,
        boolean free,
        LocalDate from,
        LocalDate to,
        String dateKey,
        Near near) {

    /** 중심 좌표와 반경(m) */
    public record Near(double lat, double lon, double radiusMeters) {
    }

    /** 의미 검색을 할 문장이 있는지 */
    public boolean semantic() {
        return text != null && !text.isBlank();
    }
}
