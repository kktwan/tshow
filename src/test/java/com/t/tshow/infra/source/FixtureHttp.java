package com.t.tshow.infra.source;

import com.t.tshow.infra.http.ApiClient;
import com.t.tshow.infra.http.ApiRequest;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 테스트용 가짜 HTTP: 주소에 들어 있는 조각으로 어떤 저장된 응답을 줄지 정한다. 호출한 주소를 기록한다. */
public class FixtureHttp implements ApiClient {

    public final List<String> urls = new ArrayList<>();
    private final List<String[]> routes = new ArrayList<>();

    /** urlPart 가 주소에 들어 있으면 resource(테스트 리소스 경로)를 돌려준다. 먼저 등록한 것이 우선 */
    public FixtureHttp on(String urlPart, String resource) {
        routes.add(new String[]{urlPart, resource});
        return this;
    }

    @Override
    public String get(ApiRequest request) {
        String url = request.url();
        String label = request.label();
        urls.add(url);
        for (String[] r : routes) {
            if (url.contains(r[0])) return read(r[1]);
        }
        throw new IllegalStateException("예상하지 못한 호출: " + label);
    }

    private static String read(String resource) {
        if (resource.startsWith("inline:")) return resource.substring("inline:".length());
        try {
            return new String(new ClassPathResource(resource).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
