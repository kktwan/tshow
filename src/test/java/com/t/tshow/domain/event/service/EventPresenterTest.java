package com.t.tshow.domain.event.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventPresenterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private final EventPresenter presenter = new EventPresenter();

    @Test
    void 하루짜리_행사는_날짜_하나만_보인다() {
        EventPresenter.Schedule s = presenter.schedule(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28), TODAY);
        assertEquals("10.28 (수)", s.period());
        assertEquals("D-20", s.status());
        assertEquals("soon", s.tone());
    }

    @Test
    void 종료일이_없으면_시작일_하루로_본다() {
        assertEquals("10.28 (수)", presenter.schedule(LocalDate.of(2026, 10, 28), null, TODAY).period());
    }

    @Test
    void 기간이_있으면_범위로_보이고_해가_다르면_해를_붙인다() {
        EventPresenter.Schedule s = presenter.schedule(LocalDate.of(2026, 8, 13), LocalDate.of(2027, 1, 3), TODAY);
        assertEquals("8.13 (목) – 2027.1.3 (일)", s.period());
        assertEquals("진행 중", s.status());
        assertEquals("live", s.tone());
    }

    @Test
    void 진행_상태_표현() {
        assertEquals("내일 시작", presenter.schedule(TODAY.plusDays(1), TODAY.plusDays(3), TODAY).status());
        assertEquals("오늘 종료", presenter.schedule(TODAY.minusDays(3), TODAY, TODAY).status());
        assertEquals("종료", presenter.schedule(TODAY.minusDays(9), TODAY.minusDays(1), TODAY).status());
        assertEquals("ended", presenter.schedule(TODAY.minusDays(9), TODAY.minusDays(1), TODAY).tone());
    }

    @Test
    void 시작일을_모르면_일정_미정이다() {
        assertEquals("일정 미정", presenter.schedule(null, null, TODAY).period());
    }
}
