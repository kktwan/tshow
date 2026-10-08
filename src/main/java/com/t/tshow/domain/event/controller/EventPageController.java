package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.dto.EventDetail;
import com.t.tshow.domain.event.service.EventService;
import com.t.tshow.global.util.Texts;
import com.t.tshow.global.web.PageMeta;
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

    /** 공유·검색 결과에 보일 한두 문장: 분류, 기간, 장소 (소개글이 있으면 앞부분) */
    private static String describe(EventDetail e) {
        StringBuilder sb = new StringBuilder(e.categoryName()).append(" · ").append(e.period());
        if (e.venueName() != null) sb.append(" · ").append(e.venueName());
        if (e.description() != null) {
            String intro = Texts.collapseSpaces(e.description());
            sb.append(". ").append(intro.length() > 100 ? intro.substring(0, 100) + "…" : intro);
        }
        return sb.toString();
    }

    @GetMapping("/events/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        EventDetail event = events.get(id);
        model.addAttribute("event", event);
        model.addAttribute("meta", new PageMeta(describe(event), event.imageUrl(), "/events/" + event.id(), false));
        String place = event.address() != null ? event.address() : event.venueName();
        model.addAttribute("mapUrl", place == null ? null : MAP_SEARCH_URL + UriUtils.encodePath(place, StandardCharsets.UTF_8));
        return "event-detail";
    }
}
