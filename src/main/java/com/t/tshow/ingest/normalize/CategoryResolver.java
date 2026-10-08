package com.t.tshow.ingest.normalize;

import com.t.tshow.ingest.source.SourceType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 소스별 분류 이름·코드를 표준 분류로 바꾼다. 변환표는 taxonomy/categories.yml 에 있고 코드에는 분류 이름이 없다.
 */
@Component
public class CategoryResolver {

    private static final String RESOURCE = "taxonomy/categories.yml";

    /** 표준 분류 하나 */
    public record Category(String id, String name, String kind) {
    }

    private final Map<String, Category> categories = new HashMap<>();
    private final Map<SourceType, Map<String, String>> mappings = new HashMap<>();
    private final Map<SourceType, String> fallback = new HashMap<>();

    public CategoryResolver() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            for (Map<String, Object> c : (List<Map<String, Object>>) root.get("categories")) {
                Category category = new Category(str(c.get("id")), str(c.get("name")), str(c.get("kind")));
                categories.put(category.id(), category);
            }
            ((Map<Object, Object>) root.get("fallback")).forEach((k, v) -> {
                requireCategory(str(v));
                fallback.put(SourceType.valueOf(str(k)), str(v));
            });
            ((Map<Object, Map<Object, Object>>) root.get("mappings")).forEach((source, map) -> {
                Map<String, String> byValue = new HashMap<>();
                map.forEach((value, id) -> {
                    requireCategory(str(id));
                    byValue.put(str(value), str(id));
                });
                mappings.put(SourceType.valueOf(str(source)), byValue);
            });
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했어요", e);
        }
    }

    private void requireCategory(String id) {
        if (!categories.containsKey(id)) {
            throw new IllegalStateException(RESOURCE + " 에 정의되지 않은 표준 분류: " + id);
        }
    }

    /** 표준 분류의 사람이 읽는 이름 (예: theater → 연극). 모르면 id 를 그대로 */
    public String name(String categoryId) {
        Category c = categories.get(categoryId);
        return c == null ? categoryId : c.name();
    }

    /** 변환하지 못하면 소스의 기본 분류를 돌려주고 report 에 남긴다 */
    public Category resolve(SourceType source, String sourceCategory, NormalizationReport report) {
        String id = sourceCategory == null ? null : mappings.getOrDefault(source, Map.of()).get(sourceCategory.trim());
        if (id == null) {
            report.unmapped("category:" + source, sourceCategory);
            id = fallback.get(source);
        }
        return categories.get(id);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
