package com.t.tshow.infra.http;

/**
 * 외부 API 를 정중하게 호출하는 규칙.
 *
 * @param intervalMillis 호출 사이 최소 간격(ms)
 * @param maxRetries     재시도 횟수
 * @param backoffMillis  재시도 전 대기(ms). 시도 횟수만큼 곱한다
 * @param timeoutSeconds 연결·응답 제한 시간(초)
 */
public record ApiPolicy(long intervalMillis, int maxRetries, long backoffMillis, int timeoutSeconds) {
}
