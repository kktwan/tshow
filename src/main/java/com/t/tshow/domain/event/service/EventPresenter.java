package com.t.tshow.domain.event.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** 행사 기간을 사람이 읽는 글로 바꾼다 (카드와 상세가 같은 표기를 쓰도록 한 곳에 둔다) */
@Component
public class EventPresenter {

    /** 기간 글, 진행 상태 글, 상태 색 구분(soon/live/ended) */
    public record Schedule(String period, String status, String tone) {
    }

    private static final DateTimeFormatter THIS_YEAR = DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN);
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("yyyy.M.d (E)", Locale.KOREAN);

    public Schedule schedule(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null) return new Schedule("일정 미정", null, "ended");
        LocalDate last = end == null ? start : end;
        String period = format(start, today).equals(format(last, today)) ? format(start, today)
                : format(start, today) + " – " + format(last, today);
        return new Schedule(period, status(start, last, today), tone(start, last, today));
    }

    private static String status(LocalDate start, LocalDate end, LocalDate today) {
        if (end.isBefore(today)) return "종료";
        if (start.isAfter(today)) {
            long days = ChronoUnit.DAYS.between(today, start);
            return days == 1 ? "내일 시작" : "D-" + days;
        }
        return end.isEqual(today) ? "오늘 종료" : "진행 중";
    }

    private static String tone(LocalDate start, LocalDate end, LocalDate today) {
        if (end.isBefore(today)) return "ended";
        return start.isAfter(today) ? "soon" : "live";
    }

    private static String format(LocalDate date, LocalDate today) {
        return (date.getYear() == today.getYear() ? THIS_YEAR : OTHER_YEAR).format(date);
    }
}
