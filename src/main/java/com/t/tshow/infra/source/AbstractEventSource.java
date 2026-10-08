package com.t.tshow.infra.source;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.source.EventSource;
import com.t.tshow.domain.ingest.source.IngestContext;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.infra.http.ApiClient;
import com.t.tshow.infra.http.ApiRequest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 공공 API 어댑터들이 함께 쓰는 뼈대: 인증키 확인, 요청 만들기, 조회 기간 나누기, 항목 한 건의 건너뜀·실패 처리.
 * 어댑터는 소스마다 다른 부분(주소·응답 읽기)만 구현한다.
 */
public abstract class AbstractEventSource implements EventSource {

    protected final IngestProperties.Source config;
    protected final ApiClient http;
    private final SourceType type;
    private final String apiKey;
    private final String keyParam;

    /**
     * @param keyParam 인증키를 담는 쿼리 파라미터 이름 (소스마다 다르다)
     */
    protected AbstractEventSource(SourceType type, IngestProperties.Source config, ApiClient http, String apiKey, String keyParam) {
        this.type = type;
        this.config = config;
        this.http = http;
        this.apiKey = apiKey;
        this.keyParam = keyParam;
    }

    @Override
    public SourceType type() {
        return type;
    }

    /** 이 소스의 기본 주소 + 경로 + 인증키 */
    protected ApiRequest request(String path) {
        return ApiRequest.to(config.baseUrl(), path).secret(keyParam, apiKey);
    }

    protected void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(type + "_API_KEY 가 설정되지 않았어요");
        }
    }

    /** 조회 기간 한도(windowDays)마다 나눠서 한 구간씩 처리한다 */
    protected void forEachWindow(LocalDate from, LocalDate to, BiConsumer<LocalDate, LocalDate> body) {
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = start.plusDays(config.windowDays());
            if (end.isAfter(to)) end = to;
            body.accept(start, end);
            start = end.plusDays(1);
        }
    }

    /** 한 번의 수집 동안 같은 항목을 두 번 처리하지 않게 지켜보는 배달원 */
    protected Delivery delivery(IngestContext context) {
        return new Delivery(context);
    }

    protected final class Delivery {

        private final IngestContext context;
        private final Set<String> seen = new HashSet<>();

        private Delivery(IngestContext context) {
            this.context = context;
        }

        /**
         * 항목 하나를 처리한다: 이미 본 항목이면 무시하고, 최근에 수집한 항목이면 건너뛰고(상세 조회 없이),
         * 아니면 상세를 가져와 넘긴다. 상세 조회가 실패해도 다음 항목은 계속한다.
         */
        public void deliver(String id, Supplier<RawEvent> fetchDetail) {
            if (id == null || !seen.add(id)) return;
            if (context.isFresh(type, id)) {
                context.skipped();
                return;
            }
            try {
                context.emit(fetchDetail.get());
            } catch (RuntimeException e) {
                context.failed(type, id, e);
            }
        }
    }
}
