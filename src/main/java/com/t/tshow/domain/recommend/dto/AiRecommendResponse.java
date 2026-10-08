package com.t.tshow.domain.recommend.dto;

import java.util.List;

/**
 * AI 추천 결과.
 *
 * @param summary     AI 가 쓴 한 줄 요약 (없을 수 있다)
 * @param picks       고른 행사들. AI 를 못 썼을 때는 검색 순서 그대로의 상위 행사 (이유 없음)
 * @param ai          AI 가 실제로 고른 결과인지. false 면 안내 문구(message)를 함께 보여 준다
 * @param message     안내 문구 (AI 를 못 썼거나, 맞는 행사가 없을 때)
 * @param interpreted 검색어에서 뽑아 적용한 조건 이름들
 */
public record AiRecommendResponse(String summary, List<AiPick> picks, boolean ai, String message, List<String> interpreted) {

    public static AiRecommendResponse notice(String message, List<String> interpreted) {
        return new AiRecommendResponse(null, List.of(), false, message, interpreted);
    }
}
