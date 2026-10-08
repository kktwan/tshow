package com.t.tshow.domain.ingest.repository;

import com.t.tshow.domain.ingest.entity.IngestRun;
import com.t.tshow.domain.ingest.entity.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {

    /** 주어진 상태로 끝난 수집 중 가장 최근에 끝난 시각. 없으면 null */
    @Query("select max(r.finishedAt) from IngestRun r where r.status in :statuses")
    Instant findLastFinishedAt(@Param("statuses") Collection<RunStatus> statuses);
}
