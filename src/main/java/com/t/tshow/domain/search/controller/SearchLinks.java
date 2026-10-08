package com.t.tshow.domain.search.controller;

import com.t.tshow.domain.search.dto.SearchRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 현재 검색 조건을 유지한 채 일부만 바꾼 주소를 만든다 (선택 버튼·쪽 이동 링크용).
 * 쪽 번호는 조건이 바뀌면 처음으로 돌아가도록 따로 지정하지 않으면 버린다.
 */
final class SearchLinks {

    private final Map<String, String> current = new LinkedHashMap<>();

    SearchLinks(SearchRequest r) {
        put("q", r.q());
        put("kind", r.kind());
        put("category", r.category());
        put("sido", r.sido());
        put("sigungu", r.sigungu());
        put("when", r.when());
        put("date", r.date() == null ? null : r.date().toString());
        put("free", Boolean.TRUE.equals(r.free()) ? "true" : null);
        put("lat", r.lat() == null ? null : r.lat().toString());
        put("lon", r.lon() == null ? null : r.lon().toString());
        put("radiusKm", r.radiusKm() == null ? null : r.radiusKm().toString());
    }

    private void put(String key, String value) {
        if (value != null && !value.isBlank()) current.put(key, value.trim());
    }

    /** 현재 조건에 changes 를 덮어쓴 주소. 값이 null 이면 그 조건을 뺀다 */
    String with(Map<String, String> changes) {
        Map<String, String> merged = new LinkedHashMap<>(current);
        changes.forEach((k, v) -> {
            if (v == null) merged.remove(k);
            else merged.put(k, v);
        });
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/");
        merged.forEach(builder::queryParam);
        return builder.build().encode().toUriString();
    }

    /** 한 가지만 바꾼 주소 */
    String with(String key, String value) {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(key, value);
        return with(changes);
    }

    /** 현재 조건 중 이 키의 값 */
    String get(String key) {
        return current.get(key);
    }

    /** 검색어를 뺀 현재 조건 (검색 입력 칸의 폼이 함께 보낼 숨은 값) */
    Map<String, String> hiddenFields() {
        Map<String, String> fields = new LinkedHashMap<>(current);
        fields.remove("q");
        return fields;
    }

    /** 검색어나 조건이 하나라도 있는지 */
    boolean any() {
        return !current.isEmpty();
    }
}
