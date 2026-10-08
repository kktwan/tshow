package com.t.tshow.domain.search.controller;

import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.search.dto.ChipView;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.domain.search.service.DateRule;
import com.t.tshow.domain.search.service.SearchDictionary;
import com.t.tshow.domain.search.service.SearchService;
import com.t.tshow.global.config.SearchProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 검색 화면 (첫 화면). 조건 버튼들은 모두 주소(링크)라서 자바스크립트 없이도 동작한다 */
@Controller
public class SearchPageController {

    private final SearchService search;
    private final SearchDictionary dictionary;
    private final CategoryResolver categories;
    private final RegionResolver regions;
    private final SearchProperties properties;

    public SearchPageController(SearchService search, SearchDictionary dictionary, CategoryResolver categories,
                                RegionResolver regions, SearchProperties properties) {
        this.search = search;
        this.dictionary = dictionary;
        this.categories = categories;
        this.regions = regions;
        this.properties = properties;
    }

    @GetMapping("/")
    public String home(@ModelAttribute("req") SearchRequest request, Model model) {
        SearchResponse result = search.search(request);
        SearchLinks links = new SearchLinks(request);

        model.addAttribute("result", result);
        model.addAttribute("filtered", links.any());
        model.addAttribute("pageTitle", links.any()
                ? (result.query().isBlank() ? "검색 결과" : result.query()) + " · tshow" : "tshow · 공연 전시 축제 찾기");
        model.addAttribute("hidden", links.hiddenFields());
        model.addAttribute("kindTabs", kindTabs(request, links));
        model.addAttribute("categoryChips", categoryChips(request, links));
        model.addAttribute("whenChips", whenChips(request, links));
        model.addAttribute("freeChip", new ChipView("무료만", links.with("free", Boolean.TRUE.equals(request.free()) ? null : "true"),
                Boolean.TRUE.equals(request.free())));
        model.addAttribute("sidoOptions", regions.sidoOptions());
        model.addAttribute("selectedSido", request.sido());
        boolean near = request.lat() != null && request.lon() != null;
        model.addAttribute("near", near);
        model.addAttribute("radiusChips", near ? radiusChips(request, links) : List.of());
        model.addAttribute("nearOffHref", links.with(nearOff()));
        model.addAttribute("prevHref", result.page() > 0 ? links.with("page", String.valueOf(result.page() - 1)) : null);
        model.addAttribute("nextHref", result.page() + 1 < result.totalPages() ? links.with("page", String.valueOf(result.page() + 1)) : null);
        model.addAttribute("resetHref", "/");
        return "search";
    }

    private List<ChipView> kindTabs(SearchRequest r, SearchLinks links) {
        List<ChipView> tabs = new ArrayList<>();
        boolean none = isBlank(r.kind());
        tabs.add(new ChipView("전체", links.with(clearKind()), none));
        categories.kinds().forEach((kind, name) -> {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("kind", kind);
            changes.put("category", null);
            tabs.add(new ChipView(name, links.with(changes), kind.equals(r.kind())));
        });
        return tabs;
    }

    /** 고른 종류(또는 고른 분류가 속한 종류)의 세부 분류 버튼 */
    private List<ChipView> categoryChips(SearchRequest r, SearchLinks links) {
        String kind = r.kind();
        if (isBlank(kind) && !isBlank(r.category())) {
            kind = categories.all().stream().filter(c -> c.id().equals(r.category())).map(CategoryResolver.Category::kind)
                    .findFirst().orElse(null);
        }
        if (isBlank(kind)) return List.of();
        String selectedKind = kind;
        return categories.all().stream().filter(c -> c.kind().equals(selectedKind)).map(c -> {
            boolean active = c.id().equals(r.category());
            return new ChipView(c.name(), links.with("category", active ? null : c.id()), active);
        }).toList();
    }

    private List<ChipView> whenChips(SearchRequest r, SearchLinks links) {
        Map<DateRule, String> labels = dictionary.dateLabels();
        List<ChipView> chips = new ArrayList<>();
        for (String key : properties.quickDates()) {
            DateRule rule = DateRule.fromKey(key);
            if (rule == null || !labels.containsKey(rule)) continue;
            boolean active = key.equals(r.when()) && r.date() == null;
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("when", active ? null : key);
            changes.put("date", null);
            chips.add(new ChipView(labels.get(rule), links.with(changes), active));
        }
        return chips;
    }

    private List<ChipView> radiusChips(SearchRequest r, SearchLinks links) {
        int current = r.radiusKm() != null ? r.radiusKm() : properties.defaultRadiusKm();
        return properties.radiusOptionsKm().stream()
                .map(km -> new ChipView(km + "km", links.with("radiusKm", String.valueOf(km)), km == current)).toList();
    }

    private static Map<String, String> clearKind() {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("kind", null);
        changes.put("category", null);
        return changes;
    }

    private static Map<String, String> nearOff() {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("lat", null);
        changes.put("lon", null);
        changes.put("radiusKm", null);
        return changes;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
