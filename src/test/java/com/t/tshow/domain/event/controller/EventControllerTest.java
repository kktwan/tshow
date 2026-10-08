package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.dto.EventResponse;
import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.service.EventService;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import com.t.tshow.global.exception.GlobalExceptionHandler;
import com.t.tshow.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 컨트롤러가 공통 응답(ApiResponse)과 오류 처리 규칙을 따르는지 확인한다 (서비스는 가짜) */
class EventControllerTest {

    private final EventService service = Mockito.mock(EventService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EventController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static EventResponse response(UUID id) {
        return new EventResponse(id, "PERFORMANCE", "theater", "어린왕자", null, LocalDate.of(2026, 10, 28), null, "시흥아트센터",
                null, "41", null, null, null, "FREE", null, null, null, null, null, null, null, null, null);
    }

    @Test
    void 목록은_성공_응답으로_감싸고_조건을_서비스에_넘긴다() throws Exception {
        UUID id = UUID.randomUUID();
        EventSearchCondition condition = new EventSearchCondition("theater", "41", null, LocalDate.of(2026, 10, 8), null, null);
        when(service.search(eq(condition), eq(0), eq(20))).thenReturn(new PageResponse<>(List.of(response(id)), 0, 20, 1, 1));

        mvc.perform(get("/api/events").param("category", "theater").param("sido", "41").param("from", "2026-10-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("어린왕자"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void 없는_행사는_404_와_오류_응답을_준다() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/events/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 형식이_틀린_값은_400_이다() throws Exception {
        mvc.perform(get("/api/events").param("from", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
