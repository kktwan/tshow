package com.t.tshow.domain.event.repository;

import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 조건이 상황에 따라 바뀌는 행사 조회 (QueryDSL) */
public interface EventQueryRepository {

    /** 논리삭제되지 않은 행사 중 조건에 맞는 것을 시작일 순으로 */
    Page<Event> search(EventSearchCondition condition, Pageable pageable);
}
