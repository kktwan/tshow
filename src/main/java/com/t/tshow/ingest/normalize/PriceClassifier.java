package com.t.tshow.ingest.normalize;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 가격 안내 문장에서 무료/유료를 가른다. 판단 규칙은 taxonomy/price.yml 에 있다.
 * 금액이 하나라도 있으면 유료, 금액 없이 무료 표현만 있으면 무료, 그 밖에는 알 수 없음이다.
 */
@Component
public class PriceClassifier {

    public enum PriceType { FREE, PAID, UNKNOWN }

    private static final String RESOURCE = "taxonomy/price.yml";

    private final List<String> freeKeywords;
    private final List<Pattern> paidPatterns;

    @SuppressWarnings("unchecked")
    public PriceClassifier() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            freeKeywords = ((List<Object>) root.get("free-keywords")).stream()
                    .map(v -> String.valueOf(v).toLowerCase(Locale.ROOT)).toList();
            paidPatterns = ((List<Object>) root.get("paid-patterns")).stream()
                    .map(v -> Pattern.compile(String.valueOf(v))).toList();
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + " 를 읽지 못했어요", e);
        }
    }

    public PriceType classify(String priceText) {
        if (priceText == null || priceText.isBlank()) return PriceType.UNKNOWN;
        for (Pattern p : paidPatterns) {
            if (p.matcher(priceText).find()) return PriceType.PAID;
        }
        String lower = priceText.toLowerCase(Locale.ROOT);
        for (String keyword : freeKeywords) {
            if (lower.contains(keyword)) return PriceType.FREE;
        }
        return PriceType.UNKNOWN;
    }
}
