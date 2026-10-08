package com.t.tshow.infra.http;

/** 외부 API 를 호출해 응답 본문을 돌려주는 공통 창구. 실제 구현은 {@link RestApiClient}, 테스트는 저장된 응답을 쓴다. */
public interface ApiClient {

    /** 호출이 재시도 후에도 실패하면 {@link ApiCallException} */
    String get(ApiRequest request);
}
