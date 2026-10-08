package com.t.tshow.domain.search.service;

import com.t.tshow.domain.search.dto.EventCard;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 검색 평가 세트(search-eval/cases.yml)를 실제 검색 서비스로 돌려 사례별 통과 여부와 점수를 보여 준다.
 * 로컬 Postgres·Qdrant 에 색인된 데이터와 OpenAI 임베딩 키가 필요해서 RUN_SEARCH_EVAL=true 일 때만 돈다.
 * 검색 품질을 고칠 때는 고치기 전과 후에 이 세트를 돌려 통과 수와 점수 분포를 비교한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_SEARCH_EVAL", matches = "true")
class SearchEvalTest {

    @Autowired
    private SearchService search;
    @Autowired
    private Clock clock;

    @Test
    @SuppressWarnings("unchecked")
    void 평가_세트를_돌린다() throws Exception {
        Map<String, Object> root;
        try (InputStream in = new ClassPathResource("search-eval/cases.yml").getInputStream()) {
            root = new Yaml().load(in);
        }
        LocalDate today = LocalDate.now(clock);
        List<String> failures = new ArrayList<>();
        int passed = 0;
        List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
        System.out.printf("%n%-28s %-6s %-8s %s%n", "사례", "건수", "최고점", "결과");
        for (Map<String, Object> c : cases) {
            String query = String.valueOf(c.get("query"));
            SearchResponse r = search.search(new SearchRequest(query, null, null, null, null, null, null, null, null, null, null, 0, 48));
            List<String> problems = check(r, (Map<String, Object>) c.get("expect"), today);
            Double top = r.items().stream().map(EventCard::score).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            System.out.printf("%-28s %-6d %-8s %s%n", c.get("name"), r.totalItems(), top == null ? "-" : String.format("%.3f", top),
                    problems.isEmpty() ? "통과" : "실패: " + problems);
            if (problems.isEmpty()) passed++;
            else failures.add(c.get("name") + " → " + problems);
        }
        System.out.printf("%n통과 %d / %d%n", passed, cases.size());
        assertTrue(failures.isEmpty(), "실패한 사례:\n" + String.join("\n", failures));
    }

    @SuppressWarnings("unchecked")
    private List<String> check(SearchResponse r, Map<String, Object> expect, LocalDate today) {
        List<String> problems = new ArrayList<>();
        List<EventCard> items = r.items();
        if (expect.containsKey("min-results") && r.totalItems() < (int) expect.get("min-results")) {
            problems.add("결과 " + r.totalItems() + "건 (최소 " + expect.get("min-results") + ")");
        }
        if (expect.containsKey("max-results") && r.totalItems() > (int) expect.get("max-results")) {
            problems.add("결과 " + r.totalItems() + "건 (최대 " + expect.get("max-results") + ")");
        }
        Map<String, Object> all = (Map<String, Object>) expect.get("all");
        if (all != null) {
            for (EventCard e : items) {
                if (all.containsKey("region-prefix") && (e.regionName() == null || !e.regionName().startsWith(String.valueOf(all.get("region-prefix"))))) {
                    problems.add("지역 불일치: " + e.title() + " (" + e.regionName() + ")");
                }
                if (all.containsKey("price") && !String.valueOf(all.get("price")).equals(e.priceType())) {
                    problems.add("가격 불일치: " + e.title());
                }
                if (all.containsKey("category") && !String.valueOf(all.get("category")).equals(e.categoryName())) {
                    problems.add("분류 불일치: " + e.title() + " (" + e.categoryName() + ")");
                }
                if (all.containsKey("period-overlaps")) {
                    LocalDate[] range = DateRule.fromKey(String.valueOf(all.get("period-overlaps"))).range(today);
                    LocalDate end = e.endDate() == null ? e.startDate() : e.endDate();
                    if (e.startDate().isAfter(range[1]) || end.isBefore(range[0])) problems.add("기간 불일치: " + e.title());
                }
                if (problems.size() > 3) break;
            }
        }
        Map<String, Object> top = (Map<String, Object>) expect.get("top");
        if (top != null) {
            int n = (int) top.get("n");
            List<String> categories = top.containsKey("category") ? (List<String>) top.get("category") : List.of();
            List<String> words = top.containsKey("title-contains") ? (List<String>) top.get("title-contains") : List.of();
            long match = items.stream().limit(n).filter(e -> categories.contains(e.categoryName())
                    || words.stream().anyMatch(w -> e.title().contains(w))).count();
            int required = (int) top.get("min-match");
            if (match < required) problems.add("위 " + n + "건 중 맞는 것 " + match + "건 (최소 " + required + ")");
        }
        return problems;
    }
}
