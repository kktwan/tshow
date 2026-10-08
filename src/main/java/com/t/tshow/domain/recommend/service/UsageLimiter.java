package com.t.tshow.domain.recommend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** 하루 AI 추천 횟수 제한: 한 사람(IP)당 한도와 서비스 전체 한도(비용 상한). 날짜가 바뀌면 초기화한다 */
public class UsageLimiter {

    private final int perClient;
    private final int total;
    private final Clock clock;
    private final Map<String, Integer> usage = new HashMap<>();
    private int used;
    private LocalDate day;

    public UsageLimiter(int perClient, int total, Clock clock) {
        this.perClient = perClient;
        this.total = total;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    /** 한도 안이면 횟수를 하나 쓰고 true. 사람별 한도나 전체 한도를 넘으면 쓰지 않고 false */
    public synchronized boolean tryConsume(String client) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            usage.clear();
            used = 0;
            day = today;
        }
        int mine = usage.getOrDefault(client, 0);
        if (mine >= perClient || used >= total) return false;
        usage.put(client, mine + 1);
        used++;
        return true;
    }
}
