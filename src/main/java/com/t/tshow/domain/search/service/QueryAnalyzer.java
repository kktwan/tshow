package com.t.tshow.domain.search.service;

import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.search.dto.SearchQuery;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.global.config.SearchProperties;
import com.t.tshow.global.util.Texts;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 검색어와 화면 선택을 {@link SearchQuery} 로 해석한다.
 * 검색어 속의 날짜·지역·무료·분류 표현은 조건으로 뽑아 문장에서 빼고, 남은 문장만 의미 검색에 쓴다.
 * 화면에서 직접 고른 값은 검색어 속 표현보다 우선한다. 어떤 말이 조건인지는 사전(query-dictionary.yml)에 있다.
 */
@Component
public class QueryAnalyzer {

    /** 의미 검색에 쓰기에는 너무 짧은 문장 (한 글자 남은 조사 등) */
    private static final int MIN_TEXT_LENGTH = 2;

    private final SearchDictionary dictionary;
    private final RegionResolver regions;
    private final SearchProperties properties;

    public QueryAnalyzer(SearchDictionary dictionary, RegionResolver regions, SearchProperties properties) {
        this.dictionary = dictionary;
        this.regions = regions;
        this.properties = properties;
    }

    public SearchQuery analyze(SearchRequest request, LocalDate today) {
        String text = Texts.collapseSpaces(request.q());
        if (text.length() > properties.maxQueryLength()) text = text.substring(0, properties.maxQueryLength());

        // 날짜: 화면 선택(특정 날짜, 버튼) → 검색어 속 표현
        DateRule rule = null;
        for (SearchDictionary.Phrase<DateRule> phrase : dictionary.datePhrases()) {
            if (text.contains(phrase.text())) {
                rule = phrase.target();
                text = remove(text, phrase.text());
                break;
            }
        }
        LocalDate from = today;
        LocalDate to = null;
        String dateKey = null;
        DateRule chosen = DateRule.fromKey(request.when());
        if (request.date() != null) {
            from = request.date().isBefore(today) ? today : request.date();
            to = from;
        } else if (chosen != null || rule != null) {
            DateRule use = chosen != null ? chosen : rule;
            LocalDate[] range = use.range(today);
            from = range[0];
            to = range[1];
            dateKey = use.key();
        }

        // 지역
        String sido = Texts.blankToNull(request.sido());
        String sigungu = Texts.blankToNull(request.sigungu());
        RegionResolver.Mention mention = regions.findIn(text, dictionary.regionParticles());
        if (mention != null) {
            for (String word : mention.matchedWords()) text = removeWord(text, word);
            if (sido == null) {
                sido = mention.sidoCode();
                sigungu = mention.sigunguCode();
            }
        }

        // 무료
        boolean free = Boolean.TRUE.equals(request.free());
        for (String phrase : dictionary.freePhrases()) {
            if (text.contains(phrase)) {
                free = true;
                text = remove(text, phrase);
            }
        }

        // 분류·종류
        Set<String> categories = new LinkedHashSet<>();
        for (SearchDictionary.Phrase<String> phrase : dictionary.categoryPhrases()) {
            if (text.contains(phrase.text())) {
                categories.add(phrase.target());
                text = remove(text, phrase.text());
            }
        }
        Set<String> kinds = new LinkedHashSet<>();
        for (SearchDictionary.Phrase<String> phrase : dictionary.kindPhrases()) {
            if (text.contains(phrase.text())) {
                kinds.add(phrase.target());
                text = remove(text, phrase.text());
            }
        }
        if (Texts.blankToNull(request.category()) != null) categories = new LinkedHashSet<>(List.of(request.category().trim()));
        if (Texts.blankToNull(request.kind()) != null) kinds = new LinkedHashSet<>(List.of(request.kind().trim()));

        for (String filler : dictionary.fillers()) text = removeWord(text, filler);
        text = Texts.collapseSpaces(text);
        if (text.length() < MIN_TEXT_LENGTH) text = "";

        return new SearchQuery(text, new ArrayList<>(kinds), new ArrayList<>(categories), sido, sigungu, free, from, to,
                dateKey, near(request));
    }

    private SearchQuery.Near near(SearchRequest r) {
        if (r.lat() == null || r.lon() == null || Math.abs(r.lat()) > 90 || Math.abs(r.lon()) > 180) return null;
        int km = r.radiusKm() != null && r.radiusKm() > 0 ? r.radiusKm() : properties.defaultRadiusKm();
        return new SearchQuery.Near(r.lat(), r.lon(), km * 1000.0);
    }

    /** 표현을 모두 지운다 (표현 사이 공백은 나중에 정리) */
    private static String remove(String text, String phrase) {
        return text.replace(phrase, " ");
    }

    /** 앞뒤가 글자가 아닌 자리에서만 낱말 하나를 지운다 (짧은 군더더기가 다른 낱말 속에서 지워지지 않게) */
    private static String removeWord(String text, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])").matcher(text).replaceAll(" ");
    }
}
