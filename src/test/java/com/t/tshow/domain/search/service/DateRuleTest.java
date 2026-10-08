package com.t.tshow.domain.search.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateRuleTest {

    /** 2026-10-08 은 목요일 */
    private static final LocalDate THU = LocalDate.of(2026, 10, 8);
    private static final LocalDate SAT = LocalDate.of(2026, 10, 10);
    private static final LocalDate SUN = LocalDate.of(2026, 10, 11);

    private static LocalDate[] days(LocalDate from, LocalDate to) {
        return new LocalDate[]{from, to};
    }

    @Test
    void 오늘과_내일() {
        assertArrayEquals(days(THU, THU), DateRule.TODAY.range(THU));
        assertArrayEquals(days(THU.plusDays(1), THU.plusDays(1)), DateRule.TOMORROW.range(THU));
    }

    @Test
    void 이번_주말은_평일에는_토일_주말에는_오늘부터_남은_날이다() {
        assertArrayEquals(days(SAT, SUN), DateRule.WEEKEND.range(THU));
        assertArrayEquals(days(SAT, SUN), DateRule.WEEKEND.range(SAT), "토요일이면 오늘부터 일요일까지");
        assertArrayEquals(days(SUN, SUN), DateRule.WEEKEND.range(SUN), "일요일이면 오늘만");
    }

    @Test
    void 다음_주말은_항상_다음_토일이다() {
        assertArrayEquals(days(SAT.plusWeeks(1), SUN.plusWeeks(1)), DateRule.NEXT_WEEKEND.range(THU));
        assertArrayEquals(days(SAT.plusWeeks(1), SUN.plusWeeks(1)), DateRule.NEXT_WEEKEND.range(SAT));
        assertArrayEquals(days(SAT.plusWeeks(1), SUN.plusWeeks(1)), DateRule.NEXT_WEEKEND.range(SUN));
    }

    @Test
    void 이번_주와_이번_달은_오늘부터_끝까지_다음_달은_다음_달_전체다() {
        assertArrayEquals(days(THU, SUN), DateRule.THIS_WEEK.range(THU));
        assertArrayEquals(days(THU, LocalDate.of(2026, 10, 31)), DateRule.THIS_MONTH.range(THU));
        assertArrayEquals(days(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), DateRule.NEXT_MONTH.range(THU));
        assertArrayEquals(days(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)), DateRule.NEXT_MONTH.range(LocalDate.of(2026, 12, 15)));
    }

    @Test
    void 이름으로_규칙을_찾는다() {
        assertEquals(DateRule.NEXT_WEEKEND, DateRule.fromKey("next-weekend"));
        assertEquals("this-month", DateRule.THIS_MONTH.key());
        assertNull(DateRule.fromKey("없는규칙"));
        assertNull(DateRule.fromKey(null));
    }
}
