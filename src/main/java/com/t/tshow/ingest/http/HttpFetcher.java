package com.t.tshow.ingest.http;

/** 주소를 호출해 응답 본문을 돌려주는 창구. 실제 구현은 {@link PoliteHttp}, 테스트는 가짜 응답을 쓴다. */
public interface HttpFetcher {

    /** url 에는 인증키가 들어 있을 수 있으므로 로그에는 label 만 쓴다 */
    String get(String url, String label);
}
