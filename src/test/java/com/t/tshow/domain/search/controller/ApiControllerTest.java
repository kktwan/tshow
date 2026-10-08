package com.t.tshow.domain.search.controller;

import com.t.tshow.domain.event.controller.EventController;
import com.t.tshow.domain.event.dto.EventDetail;
import com.t.tshow.domain.event.service.EventService;
import com.t.tshow.domain.search.dto.EventCard;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.domain.search.service.SearchService;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import com.t.tshow.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 컨트롤러가 공통 응답(ApiResponse)과 오류 처리 규칙을 따르는지 확인한다 (서비스는 가짜) */
class ApiControllerTest {

    private final SearchService search = Mockito.mock(SearchService.class);
    private final EventService events = Mockito.mock(EventService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SearchApiController(search), new EventController(events))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 검색은_요청_조건을_서비스에_넘기고_성공_응답으로_감싼다() throws Exception {
        EventCard card = new EventCard(UUID.randomUUID(), "어린왕자", "공연", "연극", null, LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 10, 28), "10.28 (수)", "D-20", "soon", "시흥아트센터", "경기 시흥시", "FREE", null, 0.5, false);
        when(search.search(argThat((SearchRequest r) -> r != null && "서울 무료".equals(r.q()) && Boolean.TRUE.equals(r.free())
                && LocalDate.of(2026, 10, 10).equals(r.date()))))
                .thenReturn(new SearchResponse("", List.of("서울", "무료"), false, false, List.of(card), 0, 12, 1, 1));

        mvc.perform(get("/api/search").param("q", "서울 무료").param("free", "true").param("date", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("어린왕자"))
                .andExpect(jsonPath("$.data.interpreted[0]").value("서울"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void 날짜_형식이_틀리면_400_이다() throws Exception {
        mvc.perform(get("/api/search").param("date", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 행사_상세는_성공_응답으로_감싼다() throws Exception {
        UUID id = UUID.randomUUID();
        EventDetail detail = new EventDetail(id, "PERFORMANCE", "공연", "theater", "연극", "어린왕자", null, null, null, "일정 미정",
                null, "ended", null, null, "", null, null, "FREE", null, null, null, null, null, null, null, null, false,
                null, null, null, List.of(), List.of());
        when(events.get(id)).thenReturn(detail);

        mvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("어린왕자"));
    }

    @Test
    void 없는_행사는_404_와_오류_응답을_준다() throws Exception {
        UUID id = UUID.randomUUID();
        when(events.get(id)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/events/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
