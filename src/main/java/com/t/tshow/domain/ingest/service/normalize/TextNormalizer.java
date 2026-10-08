package com.t.tshow.domain.ingest.service.normalize;

import com.t.tshow.global.config.NormalizeProperties;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 중복 판정용으로 제목·장소 이름을 비교하기 좋은 형태로 바꾼다.
 * 지울 패턴은 설정(tshow.normalize.*)에서 오고, 그 뒤 소문자로 바꾸고 글자·숫자만 남긴다.
 */
@Component
public class TextNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final List<Pattern> titlePatterns;
    private final List<Pattern> venuePatterns;

    public TextNormalizer(NormalizeProperties properties) {
        this.titlePatterns = properties.titleStripPatterns().stream().map(Pattern::compile).toList();
        this.venuePatterns = properties.venueStripPatterns().stream().map(Pattern::compile).toList();
    }

    public String title(String title) {
        return normalize(title, titlePatterns);
    }

    public String venue(String venueName) {
        return normalize(venueName, venuePatterns);
    }

    private static String normalize(String text, List<Pattern> strip) {
        if (text == null) return "";
        String result = text;
        for (Pattern p : strip) {
            result = p.matcher(result).replaceAll(" ");
        }
        return NON_ALNUM.matcher(result.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
