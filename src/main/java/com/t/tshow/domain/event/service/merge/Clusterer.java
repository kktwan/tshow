package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.global.config.MergeProperties;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 중복으로 판정된 레코드끼리 묶는다 (union-find).
 * 한 묶음에는 같은 소스의 레코드가 둘 이상 들어가지 않는다 — 연결하면 같은 소스 레코드가 한 묶음에 모이게 되는 경우는
 * 연결하지 않고 충돌로 센다 (소스 안에서는 서로 다른 행사이므로, 판정이 잘못됐거나 데이터가 모호한 경우).
 */
@Component
public class Clusterer {

    /** 묶음 결과: 레코드 목록의 목록, 연결을 거부한 횟수 */
    public record Result(List<List<SourceRecord>> clusters, int conflicts) {
    }

    private final DuplicateMatcher matcher;
    private final MergeProperties properties;

    public Clusterer(DuplicateMatcher matcher, MergeProperties properties) {
        this.matcher = matcher;
        this.properties = properties;
    }

    public Result cluster(List<SourceRecord> records) {
        int n = records.size();
        int[] parent = new int[n];
        List<Set<String>> sources = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            sources.add(new HashSet<>(Set.of(records.get(i).getSource().name())));
        }

        // 같은 시작일 근처끼리만 비교한다 (전체 쌍 비교를 피하는 블로킹)
        Map<LocalDate, List<Integer>> byStart = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byStart.computeIfAbsent(records.get(i).getStartDate(), k -> new ArrayList<>()).add(i);
        }

        int conflicts = 0;
        int tolerance = properties.dateToleranceDays();
        for (Map.Entry<LocalDate, List<Integer>> bucket : byStart.entrySet()) {
            LocalDate date = bucket.getKey();
            List<Integer> own = bucket.getValue();
            // 같은 시작일 안에서의 쌍
            for (int x = 0; x < own.size(); x++) {
                for (int y = x + 1; y < own.size(); y++) {
                    conflicts += tryUnion(records, own.get(x), own.get(y), parent, sources);
                }
            }
            // 허용 오차 안의 이후 시작일과의 쌍 (시작일이 없는 레코드는 같은 칸끼리만 비교한다)
            for (int offset = 1; date != null && offset <= tolerance; offset++) {
                List<Integer> other = byStart.get(date.plusDays(offset));
                if (other == null) continue;
                for (int i : own) {
                    for (int j : other) {
                        conflicts += tryUnion(records, i, j, parent, sources);
                    }
                }
            }
        }

        Map<Integer, List<SourceRecord>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(records.get(i));
        }
        return new Result(new ArrayList<>(groups.values()), conflicts);
    }

    /** 중복이면 묶는다. 같은 소스 레코드가 한 묶음에 모이게 되면 묶지 않고 충돌로 1을 돌려준다 */
    private int tryUnion(List<SourceRecord> records, int i, int j, int[] parent, List<Set<String>> sources) {
        if (!matcher.isDuplicate(records.get(i), records.get(j))) return 0;
        int ri = find(parent, i);
        int rj = find(parent, j);
        if (ri == rj) return 0;
        Set<String> merged = new HashSet<>(sources.get(ri));
        int before = merged.size() + sources.get(rj).size();
        merged.addAll(sources.get(rj));
        if (merged.size() < before) return 1;
        parent[rj] = ri;
        sources.set(ri, merged);
        return 0;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
