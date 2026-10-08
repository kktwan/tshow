package com.t.tshow.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 추천 설정 (application.yml 의 tshow.recommend.*). 버튼을 눌렀을 때만 Gemini 를 부르므로 횟수·후보 수·시간을 여기서 조절한다.
 *
 * @param candidates          AI 에게 보여 줄 후보 수 (검색 결과 위에서부터). 입력 토큰과 응답 시간에 비례한다
 * @param maxPicks            AI 가 고를 수 있는 최대 행사 수. 이유(reason)를 쓰는 만큼 출력 토큰이 늘어난다
 * @param timeoutSeconds      AI 응답을 기다리는 최대 시간. 넘으면 검색 순서 그대로 보여 준다
 * @param dailyLimitPerClient 한 사람(IP)이 하루에 쓸 수 있는 횟수
 * @param dailyLimitTotal     서비스 전체가 하루에 쓸 수 있는 횟수 (비용 상한)
 * @param cacheMinutes        같은 조건의 추천 결과를 다시 쓰는 시간(분). 캐시에서 나가는 요청은 횟수에 세지 않는다
 * @param cacheSize           캐시에 둘 결과 수
 * @param descriptionChars    후보 한 건에 담을 소개글의 최대 글자 수
 */
@ConfigurationProperties(prefix = "tshow.recommend")
public record RecommendProperties(
        @DefaultValue("20") int candidates,
        @DefaultValue("8") int maxPicks,
        @DefaultValue("20") int timeoutSeconds,
        @DefaultValue("10") int dailyLimitPerClient,
        @DefaultValue("300") int dailyLimitTotal,
        @DefaultValue("30") int cacheMinutes,
        @DefaultValue("200") int cacheSize,
        @DefaultValue("90") int descriptionChars) {
}
