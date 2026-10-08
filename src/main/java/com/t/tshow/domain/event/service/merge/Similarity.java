package com.t.tshow.domain.event.service.merge;


import com.t.tshow.global.util.Geo;

import java.util.HashMap;
import java.util.Map;

/** 문자열 유사도와 좌표 거리 계산 (중복 판정용 순수 함수) */
public final class Similarity {

    private Similarity() {
    }

    /**
     * 글자 2-그램 Dice 계수 (0~1). 같은 문자열이면 1, 한쪽이 비었으면 0.
     * 짧은 문자열(한 글자)은 같을 때만 1이다.
     */
    public static double dice(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0.0;
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String gram = b.substring(i, i + 2);
            Integer count = grams.get(gram);
            if (count != null && count > 0) {
                overlap++;
                grams.put(gram, count - 1);
            }
        }
        return 2.0 * overlap / ((a.length() - 1) + (b.length() - 1));
    }

    /** 한쪽이 다른 쪽을 포함하고 짧은 쪽이 minLength 글자 이상이면 true (예: "롯데콘서트홀"과 "롯데콘서트홀대공연장") */
    public static boolean containsEither(String a, String b, int minLength) {
        if (a == null || b == null) return false;
        String shorter = a.length() <= b.length() ? a : b;
        String longer = shorter == a ? b : a;
        return shorter.length() >= minLength && longer.contains(shorter);
    }

    /** 두 좌표 사이 거리(m) */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return Geo.distanceMeters(lat1, lon1, lat2, lon2);
    }
}
