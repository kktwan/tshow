package com.t.tshow.global.web;

/**
 * 화면 하나의 검색엔진·공유용 정보 (head 의 description, canonical, 공유 미리보기).
 *
 * @param description 한두 문장 설명 (비어 있으면 기본 설명)
 * @param image       공유 미리보기 이미지 주소 (없으면 null)
 * @param canonical   대표 주소의 경로 (예: /events/ID). 없으면 null
 * @param noindex     true 면 검색엔진 색인에서 뺀다 (조건이 붙은 검색 결과처럼 끝없이 늘어나는 화면)
 */
public record PageMeta(String description, String image, String canonical, boolean noindex) {

    public static PageMeta of(String description, String canonical) {
        return new PageMeta(description, null, canonical, false);
    }
}
