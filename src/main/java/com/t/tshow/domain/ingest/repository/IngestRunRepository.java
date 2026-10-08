package com.t.tshow.domain.ingest.repository;

import com.t.tshow.domain.ingest.entity.IngestRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {
}
