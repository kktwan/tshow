package com.t.tshow.domain.ingest.source;

import com.t.tshow.domain.ingest.entity.SourceType;

import java.time.LocalDate;

/** 공공 API 하나를 감싼 어댑터. 가져온 항목을 {@link RawEvent} 로 바꿔 컨텍스트에 넘기는 일만 한다. */
public interface EventSource {

    SourceType type();

    /** from~to 사이에 열리는(겹치는) 행사를 가져온다. 소스별 조회 기간 한도는 어댑터가 나눠서 처리한다 */
    void fetch(LocalDate from, LocalDate to, IngestContext context);
}
