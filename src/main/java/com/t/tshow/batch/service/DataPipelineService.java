package com.t.tshow.batch.service;

import com.t.tshow.domain.event.service.MergeService;
import com.t.tshow.domain.index.service.IndexService;
import com.t.tshow.domain.ingest.service.IngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 데이터 갱신 파이프라인: 수집 → 병합 → 색인. 도메인 서비스를 순서대로 부르기만 하고,
 * 실행이 겹쳐 돌지 않게 막는다 (이미 돌고 있으면 건너뜀).
 */
@Service
public class DataPipelineService {

    private static final Logger log = LoggerFactory.getLogger(DataPipelineService.class);

    private final IngestService ingest;
    private final MergeService merge;
    private final IndexService index;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DataPipelineService(IngestService ingest, MergeService merge, IndexService index) {
        this.ingest = ingest;
        this.merge = merge;
        this.index = index;
    }

    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("이미 수집이 진행 중이라 건너뜀");
            return;
        }
        try {
            ingest.runAll();
            // 수집이 끝나면 소스 간 중복을 합쳐 event 에 반영한다
            merge.run();
            // 합친 행사 중 바뀐 것만 벡터 색인에 반영한다 (임베딩 키가 없으면 건너뜀)
            index.sync();
        } finally {
            running.set(false);
        }
    }
}
