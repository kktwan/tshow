package com.t.tshow.domain.recommend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class UsageLimiterTest {

    /** 시간을 마음대로 옮길 수 있는 시계 */
    private static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-08T01:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 사람별_한도와_전체_한도를_지킨다() {
        UsageLimiter limiter = new UsageLimiter(2, 3, new MovableClock());

        assertTrue(limiter.tryConsume("a"));
        assertTrue(limiter.tryConsume("a"));
        assertFalse(limiter.tryConsume("a"), "한 사람 한도(2)");
        assertTrue(limiter.tryConsume("b"));
        assertFalse(limiter.tryConsume("c"), "전체 한도(3)");
    }

    @Test
    void 날짜가_바뀌면_다시_센다() {
        MovableClock clock = new MovableClock();
        UsageLimiter limiter = new UsageLimiter(1, 10, clock);
        assertTrue(limiter.tryConsume("a"));
        assertFalse(limiter.tryConsume("a"));

        clock.now = clock.now.plus(Duration.ofDays(1));

        assertTrue(limiter.tryConsume("a"));
    }

    @Test
    void 캐시는_시간이_지나면_비운다() {
        MovableClock clock = new MovableClock();
        ResultCache<String> cache = new ResultCache<>(2, Duration.ofMinutes(30), clock);
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));

        clock.now = clock.now.plus(Duration.ofMinutes(31));
        assertNull(cache.get("k"));

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertNull(cache.get("a"), "가장 오래 안 쓴 것부터 밀려난다 (크기 2)");
        assertEquals("3", cache.get("c"));
    }
}
