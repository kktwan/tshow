package com.t.tshow.infra.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Spring {@link RestClient} 로 호출하는 공통 구현. 호출 사이 간격을 지키고, 일시 차단·서버 오류는 대기 후 재시도한다.
 * (KOPIS 는 호출이 몰리면 400 Request Blocked 로 일시 차단한다.) 간격은 호출 대상(이 인스턴스)마다 따로 지킨다.
 */
public class RestApiClient implements ApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestApiClient.class);

    private final String name;
    private final ApiPolicy policy;
    private final RestClient client;
    private long lastCallAt = 0L;

    public RestApiClient(String name, ApiPolicy policy) {
        this.name = name;
        this.policy = policy;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(policy.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(policy.timeoutSeconds()));
        this.client = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", "Mozilla/5.0").build();
    }

    @Override
    public synchronized String get(ApiRequest request) {
        URI uri = URI.create(request.url());
        for (int attempt = 0; ; attempt++) {
            throttle();
            Integer status = null;
            String failure;
            try {
                byte[] body = client.get().uri(uri).retrieve().body(byte[].class);
                lastCallAt = System.currentTimeMillis();
                return body == null ? "" : new String(body, StandardCharsets.UTF_8);
            } catch (RestClientResponseException e) {
                lastCallAt = System.currentTimeMillis();
                status = e.getStatusCode().value();
                failure = "HTTP " + status;
            } catch (ResourceAccessException e) {
                lastCallAt = System.currentTimeMillis();
                failure = e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName();
            }

            boolean retryable = status == null || status == 400 || status == 429 || status >= 500;
            if (!retryable || attempt >= policy.maxRetries()) {
                throw new ApiCallException(name + " " + request.label() + " 실패: " + failure);
            }
            long wait = policy.backoffMillis() * (attempt + 1);
            log.warn("{} {} {} → {}ms 후 재시도 ({}/{})", name, request.label(), failure, wait, attempt + 1, policy.maxRetries());
            sleep(wait);
        }
    }

    private void throttle() {
        long wait = policy.intervalMillis() - (System.currentTimeMillis() - lastCallAt);
        if (wait > 0) sleep(wait);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiCallException(name + " 중단됨", e);
        }
    }
}
