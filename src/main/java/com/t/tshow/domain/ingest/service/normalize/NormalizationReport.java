package com.t.tshow.domain.ingest.service.normalize;


import java.util.Map;
import java.util.TreeMap;

/**
 * 정규화 중 표로 변환하지 못한 값을 모은다. 예외로 터뜨리지 않고 기본값으로 저장한 뒤 수집 결과에 남겨서,
 * 사람이 표(categories.yml, region-aliases.yml)를 고칠 수 있게 한다.
 */
public class NormalizationReport {

    /** 종류(예: category:KOPIS, region) → 값 → 횟수 */
    private final Map<String, Map<String, Integer>> unmapped = new TreeMap<>();

    public void unmapped(String type, String value) {
        unmapped.computeIfAbsent(type, k -> new TreeMap<>()).merge(value == null ? "(없음)" : value, 1, Integer::sum);
    }

    public boolean isEmpty() {
        return unmapped.isEmpty();
    }

    public Map<String, Map<String, Integer>> unmapped() {
        return unmapped;
    }
}
