package com.t.tshow.index;

import com.t.tshow.event.Event;
import com.t.tshow.global.config.IndexProperties;
import com.t.tshow.ingest.normalize.CategoryResolver;
import com.t.tshow.ingest.normalize.RegionResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 행사 하나를 벡터로 바꿀 문장으로 만든다 (의미로 찾는 부분).
 * 모양은 리소스 템플릿(index/embedding.tpl)에 있고, 값이 없는 줄은 뺀다.
 *
 * <p>날짜와 가격은 넣지 않는다. 바뀌어도 다시 임베딩할 필요가 없고, 정확히 걸러야 하는 값이라 payload 필터가 맡는다.
 * 설명이 없는 행사가 많으므로(KOPIS 는 9% 정도) 설명이 없으면 그 줄만 빠지고 나머지로 구성된다.
 */
@Component
public class EmbeddingTextBuilder {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final String[] templateLines;
    private final int descriptionMaxChars;
    private final CategoryResolver categories;
    private final RegionResolver regions;

    public EmbeddingTextBuilder(IndexProperties properties, CategoryResolver categories, RegionResolver regions) {
        this.descriptionMaxChars = properties.descriptionMaxChars();
        this.categories = categories;
        this.regions = regions;
        try {
            String template = new String(new ClassPathResource(properties.embeddingTemplate()).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            this.templateLines = template.split("\\R");
        } catch (IOException e) {
            throw new IllegalStateException(properties.embeddingTemplate() + " 를 읽지 못했어요", e);
        }
    }

    public String build(Event e) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", clean(e.title()));
        values.put("category", categories.name(e.category()));
        values.put("venue", clean(e.venueName()));
        values.put("region", regions.displayName(e.sidoCode(), e.sigunguCode()));
        values.put("cast", clean(e.castText()));
        values.put("host", clean(e.hostText()));
        values.put("description", truncate(clean(e.description())));

        StringBuilder sb = new StringBuilder();
        for (String line : templateLines) {
            if (line.isBlank()) continue;
            Matcher m = PLACEHOLDER.matcher(line);
            boolean skip = false;
            StringBuffer out = new StringBuffer();
            while (m.find()) {
                String value = values.getOrDefault(m.group(1), "");
                if (value.isEmpty()) {
                    skip = true;
                    break;
                }
                m.appendReplacement(out, Matcher.quoteReplacement(value));
            }
            if (skip) continue;
            m.appendTail(out);
            if (sb.length() > 0) sb.append('\n');
            sb.append(out);
        }
        return sb.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : SPACES.matcher(s).replaceAll(" ").trim();
    }

    private String truncate(String s) {
        return s.length() <= descriptionMaxChars ? s : s.substring(0, descriptionMaxChars);
    }
}
