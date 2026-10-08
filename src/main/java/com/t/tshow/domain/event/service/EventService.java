package com.t.tshow.domain.event.service;

import com.t.tshow.domain.event.dto.EventResponse;
import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import com.t.tshow.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 행사 조회 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository events;

    public EventService(EventRepository events) {
        this.events = events;
    }

    public PageResponse<EventResponse> search(EventSearchCondition condition, int page, int size) {
        if (page < 0 || size < 1) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return PageResponse.of(events.search(condition, pageable), EventResponse::from);
    }

    public EventResponse get(UUID id) {
        return events.findById(id)
                .filter(e -> e.getArchivedAt() == null)
                .map(EventResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
