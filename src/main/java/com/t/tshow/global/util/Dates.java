package com.t.tshow.global.util;

import java.time.LocalDate;

/** 날짜 도우미 */
public final class Dates {

    private Dates() {
    }

    /** 2026-10-08 → 20261008 (공공 API 의 날짜 조회 파라미터 형식) */
    public static String compact(LocalDate date) {
        return date.toString().replace("-", "");
    }
}
