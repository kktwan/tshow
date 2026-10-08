package com.t.tshow.infra.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 API 호출 한 건: 주소(기본 주소 + 경로), 쿼리 파라미터, 로그에 쓸 설명(label).
 * 파라미터 값은 알아서 URL 인코딩한다. 인증키(secret)는 이미 인코딩된 값(% 포함)이면 다시 인코딩하지 않고,
 * 주소에는 키가 들어가므로 로그·예외에는 주소 대신 label 만 쓴다.
 */
public final class ApiRequest {

    private final String baseUrl;
    private final String path;
    private final List<String> params = new ArrayList<>();
    private String label = "";

    private ApiRequest(String baseUrl, String path) {
        this.baseUrl = baseUrl;
        this.path = path;
    }

    public static ApiRequest to(String baseUrl, String path) {
        return new ApiRequest(baseUrl, path);
    }

    public ApiRequest param(String name, Object value) {
        params.add(name + "=" + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        return this;
    }

    /** 인증키 파라미터. 이미 인코딩된 키(% 가 들어 있음)는 그대로 쓴다 */
    public ApiRequest secret(String name, String key) {
        params.add(name + "=" + (key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8)));
        return this;
    }

    public ApiRequest label(String label) {
        this.label = label;
        return this;
    }

    public String label() {
        return label;
    }

    /** 인증키가 들어 있는 전체 주소. 로그에 쓰지 않는다 */
    public String url() {
        return params.isEmpty() ? baseUrl + path : baseUrl + path + "?" + String.join("&", params);
    }
}
