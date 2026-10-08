package com.t.tshow.ingest.http;

import com.t.tshow.global.config.IngestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 공공 API 호출용 HTTP 클라이언트. 호출 사이 간격을 지키고, 일시 차단·서버 오류는 대기 후 재시도한다.
 * (KOPIS 는 호출이 몰리면 400 Request Blocked 로 일시 차단한다.)
 * 주소에는 인증키가 들어 있으므로 로그·예외에는 주소 대신 호출을 설명하는 label 만 쓴다.
 */
public class PoliteHttp implements HttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(PoliteHttp.class);

    private final String name;
    private final IngestProperties.Source config;
    private final HttpClient client;
    private long lastCallAt = 0L;

    public PoliteHttp(String name, IngestProperties.Source config) {
        this.name = name;
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public synchronized String get(String url, String label) {
        int attempt = 0;
        while (true) {
            throttle();
            Integer status = null;
            Exception failure = null;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                lastCallAt = System.currentTimeMillis();
                status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
            } catch (IOException e) {
                lastCallAt = System.currentTimeMillis();
                failure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SourceUnavailableException(name + " " + label + " 중단됨", e);
            }

            boolean retryable = failure != null || status == 400 || status == 429 || status >= 500;
            String reason = failure != null ? failure.getClass().getSimpleName() : "HTTP " + status;
            if (!retryable || attempt >= config.maxRetries()) {
                throw new SourceUnavailableException(name + " " + label + " 실패: " + reason);
            }
            attempt++;
            long wait = config.backoffMillis() * attempt;
            log.warn("{} {} {} → {}ms 후 재시도 ({}/{})", name, label, reason, wait, attempt, config.maxRetries());
            sleep(wait);
        }
    }

    private void throttle() {
        long wait = config.intervalMillis() - (System.currentTimeMillis() - lastCallAt);
        if (wait > 0) sleep(wait);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException(name + " 중단됨", e);
        }
    }
}
