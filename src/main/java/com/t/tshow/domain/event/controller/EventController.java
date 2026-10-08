package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.dto.EventDetail;
import com.t.tshow.domain.event.service.EventService;
import com.t.tshow.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetail> get(@PathVariable UUID id) {
        return ApiResponse.success(events.get(id));
    }
}
