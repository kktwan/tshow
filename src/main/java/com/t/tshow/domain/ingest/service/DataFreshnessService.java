package com.t.tshow.domain.ingest.service;

import com.t.tshow.domain.ingest.entity.RunStatus;
import com.t.tshow.domain.ingest.repository.IngestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 데이터가 언제 마지막으로 갱신됐는지 알려 준다 (화면 하단 표시용). 모든 화면이 부르므로 몇 분간 기억해 둔다 */
@Service
public class DataFreshnessService {

    private static final Duration KEEP = Duration.ofMinutes(5);

    private final IngestRunRepository runs;
    private final Clock clock;
    private Instant cachedAt = Instant.MIN;
    private Instant cached;

    public DataFreshnessService(IngestRunRepository runs, Clock clock) {
        this.runs = runs;
        this.clock = clock;
    }

    /** 성공(일부 실패 포함)한 마지막 수집의 끝난 시각. 아직 없으면 null */
    @Transactional(readOnly = true)
    public synchronized Instant lastUpdatedAt() {
        Instant now = clock.instant();
        if (cachedAt.plus(KEEP).isBefore(now)) {
            cached = runs.findLastFinishedAt(List.of(RunStatus.SUCCESS, RunStatus.PARTIAL));
            cachedAt = now;
        }
        return cached;
    }
}
