package com.t.tshow.domain.ingest.source;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;

/** 소스 어댑터가 수집 진행 중에 쓰는 창구. 어댑터는 저장·집계 방법을 모른다. */
public interface IngestContext {

    /**
     * 제공처의 목록에 이 항목이 있었음을 알린다 (최근에 수집해서 건너뛴 항목, 상세 조회에 실패한 항목도 포함).
     * 수집이 끝난 뒤 "목록에서 사라진 항목"을 가려내는 데 쓴다. 쓰지 않는 구현은 무시해도 된다.
     */
    default void listed(String sourceId) {
    }

    /** 이미 최근에 수집한 항목이면 true (상세 조회를 건너뛴다) */
    boolean isFresh(SourceType source, String sourceId);

    /** 상세까지 가져온 항목을 넘긴다 */
    void emit(RawEvent event);

    /** 최근에 수집해서 건너뛴 항목 */
    void skipped();

    /** 한 항목 수집에 실패했지만 다음 항목은 계속한다 */
    void failed(SourceType source, String sourceId, Exception cause);
}
