package com.t.tshow.domain.recommend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 일정 시간 동안만 쓰는 작은 LRU 캐시. 같은 조건의 AI 추천을 다시 부르지 않으려고 쓴다 */
public class ResultCache<V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry<V>> map;

    public ResultCache(int maxSize, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized V get(String key) {
        Entry<V> entry = map.get(key);
        if (entry == null) return null;
        if (entry.expiresAt().isBefore(clock.instant())) {
            map.remove(key);
            return null;
        }
        return entry.value();
    }

    public synchronized void put(String key, V value) {
        map.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }
}
