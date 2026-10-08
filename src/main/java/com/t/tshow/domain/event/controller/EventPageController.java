package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.dto.EventDetail;
import com.t.tshow.domain.event.service.EventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 행사 상세 화면 */
@Controller
public class EventPageController {

    private static final String MAP_SEARCH_URL = "https://map.naver.com/p/search/";

    private final EventService events;

    public EventPageController(EventService events) {
        this.events = events;
    }

    @GetMapping("/events/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        EventDetail event = events.get(id);
        model.addAttribute("event", event);
        String place = event.address() != null ? event.address() : event.venueName();
        model.addAttribute("mapUrl", place == null ? null : MAP_SEARCH_URL + UriUtils.encodePath(place, StandardCharsets.UTF_8));
        return "event-detail";
    }
}
