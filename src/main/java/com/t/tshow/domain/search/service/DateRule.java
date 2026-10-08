package com.t.tshow.domain.search.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * 날짜 표현이 가리키는 기간을 계산하는 규칙. 어떤 말이 어떤 규칙인지는 사전(query-dictionary.yml)에 있고, 여기에는 계산만 있다.
 * 기간은 [시작, 끝] 이며 시작은 오늘보다 앞서지 않는다 (이미 지난 날은 볼 일이 없다).
 */
public enum DateRule {

    TODAY {
        @Override
        public LocalDate[] range(LocalDate today) {
            return new LocalDate[]{today, today};
        }
    },
    TOMORROW {
        @Override
        public LocalDate[] range(LocalDate today) {
            return new LocalDate[]{today.plusDays(1), today.plusDays(1)};
        }
    },
    /** 이번 주 토·일. 이미 주말이면 오늘부터 일요일까지 */
    WEEKEND {
        @Override
        public LocalDate[] range(LocalDate today) {
            LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) return new LocalDate[]{today, today};
            return new LocalDate[]{max(today, saturday), saturday.plusDays(1)};
        }
    },
    NEXT_WEEKEND {
        @Override
        public LocalDate[] range(LocalDate today) {
            LocalDate saturday = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
            LocalDate thisSaturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            LocalDate target = today.getDayOfWeek() == DayOfWeek.SUNDAY || today.getDayOfWeek() == DayOfWeek.SATURDAY
                    ? saturday : thisSaturday.plusWeeks(1);
            return new LocalDate[]{target, target.plusDays(1)};
        }
    },
    /** 오늘부터 이번 주 일요일까지 */
    THIS_WEEK {
        @Override
        public LocalDate[] range(LocalDate today) {
            return new LocalDate[]{today, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))};
        }
    },
    /** 오늘부터 이번 달 말일까지 */
    THIS_MONTH {
        @Override
        public LocalDate[] range(LocalDate today) {
            return new LocalDate[]{today, today.with(TemporalAdjusters.lastDayOfMonth())};
        }
    },
    NEXT_MONTH {
        @Override
        public LocalDate[] range(LocalDate today) {
            LocalDate first = today.with(TemporalAdjusters.firstDayOfNextMonth());
            return new LocalDate[]{first, first.with(TemporalAdjusters.lastDayOfMonth())};
        }
    };

    /** 오늘 기준 [시작, 끝] */
    public abstract LocalDate[] range(LocalDate today);

    /** 사전·요청에서 쓰는 이름(this-month) 으로 규칙을 찾는다. 모르면 null */
    public static DateRule fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 사전·요청에서 쓰는 이름 (this-month) */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
