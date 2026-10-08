package com.t.tshow.infra.source;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.source.IngestContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/** 테스트용 수집 컨텍스트: 넘어온 항목을 모으고, 실패가 오면 테스트를 실패시킨다 */
public class CollectingContext implements IngestContext {

    public final List<RawEvent> events = new ArrayList<>();
    public int skipped;
    public boolean fresh;

    @Override
    public boolean isFresh(SourceType source, String sourceId) {
        return fresh;
    }

    @Override
    public void emit(RawEvent event) {
        events.add(event);
    }

    @Override
    public void skipped() {
        skipped++;
    }

    @Override
    public void failed(SourceType source, String sourceId, Exception cause) {
        fail("항목 수집 실패: " + source + " " + sourceId, cause);
    }
}
