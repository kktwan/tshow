package com.t.tshow.domain.search.service;

import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 검색어에서 조건을 뽑는 사전(search/query-dictionary.yml)을 읽는다.
 * 사전이 가리키는 날짜 규칙·분류·종류가 실제로 있는지 시작할 때 확인해서, 오타가 조용히 무시되지 않게 한다.
 */
@Component
public class SearchDictionary {

    private static final String RESOURCE = "search/query-dictionary.yml";

    /** 표현 하나와 그것이 가리키는 값 */
    public record Phrase<T>(String text, T target) {
    }

    private final Map<DateRule, List<String>> dates = new LinkedHashMap<>();
    private final List<String> free = new ArrayList<>();
    private final Map<String, List<String>> kinds = new LinkedHashMap<>();
    private final Map<String, List<String>> categories = new LinkedHashMap<>();
    private final List<String> regionParticles = new ArrayList<>();
    private final List<String> fillers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public SearchDictionary(CategoryResolver categoryResolver) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            ((Map<Object, Object>) root.get("dates")).forEach((key, phrases) -> {
                DateRule rule = DateRule.fromKey(String.valueOf(key));
                if (rule == null) throw new IllegalStateException(RESOURCE + " 에 없는 날짜 규칙: " + key);
                dates.put(rule, strings(phrases));
            });
            free.addAll(strings(root.get("free")));
            ((Map<Object, Object>) root.get("kinds")).forEach((key, phrases) -> {
                if (!categoryResolver.kinds().containsKey(String.valueOf(key))) {
                    throw new IllegalStateException(RESOURCE + " 에 없는 종류: " + key);
                }
                kinds.put(String.valueOf(key), strings(phrases));
            });
            ((Map<Object, Object>) root.get("categories")).forEach((key, phrases) -> {
                if (categoryResolver.all().stream().noneMatch(c -> c.id().equals(String.valueOf(key)))) {
                    throw new IllegalStateException(RESOURCE + " 에 없는 분류: " + key);
                }
                categories.put(String.valueOf(key), strings(phrases));
            });
            regionParticles.addAll(strings(root.get("region-particles")));
            fillers.addAll(strings(root.get("fillers")));
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했어요", e);
        }
    }

    private static List<String> strings(Object value) {
        return ((List<?>) value).stream().map(String::valueOf).toList();
    }

    /** 날짜 표현 전체. 긴 표현이 앞에 온다 */
    public List<Phrase<DateRule>> datePhrases() {
        return flatten(dates);
    }

    /** 화면 버튼에 쓸 날짜 규칙 이름(사전의 첫 번째 표현) */
    public Map<DateRule, String> dateLabels() {
        return dates.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0),
                (a, b) -> a, LinkedHashMap::new));
    }

    public List<String> freePhrases() {
        return free.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    public List<Phrase<String>> kindPhrases() {
        return flatten(kinds);
    }

    public List<Phrase<String>> categoryPhrases() {
        return flatten(categories);
    }

    public List<String> regionParticles() {
        return regionParticles;
    }

    /** 긴 것부터 */
    public List<String> fillers() {
        return fillers.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    private static <K> List<Phrase<K>> flatten(Map<K, List<String>> map) {
        List<Phrase<K>> result = new ArrayList<>();
        map.forEach((target, phrases) -> phrases.forEach(p -> result.add(new Phrase<>(p, target))));
        result.sort(Comparator.comparingInt((Phrase<K> p) -> p.text().length()).reversed());
        return result;
    }
}
