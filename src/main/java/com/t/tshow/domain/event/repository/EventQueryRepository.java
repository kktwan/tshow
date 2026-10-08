package com.t.tshow.domain.event.repository;

import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/** 조건이 상황에 따라 바뀌는 행사 조회 (QueryDSL) */
public interface EventQueryRepository {

    /** 논리삭제되지 않은 행사 중 조건에 맞는 것을, 곧 시작하거나 진행 중인 것부터 */
    Page<Event> search(EventSearchCondition condition, LocalDate today, Pageable pageable);
}
