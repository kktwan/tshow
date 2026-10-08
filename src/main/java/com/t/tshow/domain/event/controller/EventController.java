package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.dto.EventResponse;
import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.service.EventService;
import com.t.tshow.global.response.ApiResponse;
import com.t.tshow.global.response.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    /** 행사 목록: 조건(분류·시도·가격·기간·제목)에 맞는 행사를 시작일 순으로 */
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String priceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        EventSearchCondition condition = new EventSearchCondition(category, sido, priceType, from, to, keyword);
        return ApiResponse.success(events.search(condition, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(events.get(id));
    }
}
