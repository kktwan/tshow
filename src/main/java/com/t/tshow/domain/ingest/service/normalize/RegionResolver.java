package com.t.tshow.domain.ingest.service.normalize;


import com.t.tshow.global.util.Texts;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 지역 이름·코드를 한국관광공사 법정동 코드(시도 코드 + 시군구 코드)로 바꾼다.
 * 코드표는 공식 API 에서 받은 taxonomy/regions.csv, 이름 규칙으로 풀리지 않는 별칭은 taxonomy/region-aliases.yml 에 있다.
 * 풀지 못한 값은 예외로 터뜨리지 않고 report 에 남긴다.
 */
@Component
public class RegionResolver {

    /** 변환 결과. 시군구를 모르면 sigunguCode 는 null, 한국 지역이 아니거나 모르면 둘 다 null */
    public record Region(String sidoCode, String sigunguCode) {
        public static final Region NONE = new Region(null, null);
    }

    private static final String CSV = "taxonomy/regions.csv";
    private static final String ALIASES = "taxonomy/region-aliases.yml";
    /** 시도 이름 끝에서 떼어 내는 접미사. 서울시→서울 처럼 시·도는 떼지만 구·군은 떼지 않는다 (대구가 "대"가 되면 안 된다) */
    private static final List<String> SIDO_SUFFIXES = List.of("특별자치도", "특별자치시", "통합특별시", "특별시", "광역시", "도", "시");
    /** 시군구 이름 끝에서 떼어 내는 접미사 */
    private static final List<String> SIGUNGU_SUFFIXES = List.of("시", "군", "구");
    /** 읍·면·동·도로명으로 끝나는 말은 시군구 후보가 아니다 (리포트에 남기지 않는다) */
    private static final List<String> NOT_SIGUNGU_SUFFIXES = List.of("읍", "면", "동", "리", "로", "길");

    private final Map<String, String> sidoNameByCode = new HashMap<>();
    /** 시도 코드 → (시군구 코드 → 시군구 이름) */
    private final Map<String, Map<String, String>> sigunguNameByCode = new HashMap<>();
    /** 시도 코드 → (시군구 이름 → 시군구 코드) */
    private final Map<String, Map<String, String>> sigunguByNameBySido = new HashMap<>();
    private final Map<String, String> sidoByAlias = new HashMap<>();
    private final Set<String> nonDomestic = new HashSet<>();
    /** 시도 코드 → (소스가 주는 옛 시군구 이름 → 현재 이름) */
    private final Map<String, Map<String, String>> sigunguRenames = new HashMap<>();

    /** 시도 이름·줄임말·별칭 → 시도 코드 (질의 속 지역 찾기용) */
    private final Map<String, String> sidoByWord = new HashMap<>();
    /** 시군구 이름·줄임말 → 가능한 (시도 코드, 시군구 코드) 목록. 같은 이름이 여러 시도에 있을 수 있다 */
    private final Map<String, List<String[]>> sigunguByWord = new HashMap<>();

    public RegionResolver() {
        loadCsv();
        loadAliases();
        indexWords();
    }

    private void indexWords() {
        sidoNameByCode.forEach((code, name) -> {
            sidoByWord.put(name, code);
            sidoByWord.put(stem(name, SIDO_SUFFIXES), code);
        });
        sidoByWord.putAll(sidoByAlias);
        sigunguNameByCode.forEach((sido, byCode) -> byCode.forEach((code, name) -> {
            sigunguByWord.computeIfAbsent(name, k -> new ArrayList<>()).add(new String[]{sido, code});
            String stem = stem(name, SIGUNGU_SUFFIXES);
            // 한 글자 줄임말(중, 서, 동)은 일반 낱말과 구분되지 않아 쓰지 않는다
            if (stem.length() >= 2) sigunguByWord.computeIfAbsent(stem, k -> new ArrayList<>()).add(new String[]{sido, code});
        }));
    }

    private void loadCsv() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ClassPathResource(CSV).getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] c = line.split(",", -1);
                sidoNameByCode.put(c[0], c[1]);
                sigunguByNameBySido.computeIfAbsent(c[0], k -> new HashMap<>()).put(c[3], c[2]);
                sigunguNameByCode.computeIfAbsent(c[0], k -> new HashMap<>()).put(c[2], c[3]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(CSV + " 를 읽지 못했어요", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAliases() {
        try (InputStream in = new ClassPathResource(ALIASES).getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            ((Map<Object, Object>) root.get("sido")).forEach((k, v) -> sidoByAlias.put(String.valueOf(k), String.valueOf(v)));
            ((List<Object>) root.get("non-domestic")).forEach(v -> nonDomestic.add(String.valueOf(v)));
            Object renames = root.get("sigungu");
            if (renames instanceof Map<?, ?> bySido) {
                bySido.forEach((sido, names) -> {
                    Map<String, String> map = new HashMap<>();
                    ((Map<Object, Object>) names).forEach((from, to) -> map.put(String.valueOf(from), String.valueOf(to)));
                    sigunguRenames.put(String.valueOf(sido), map);
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException(ALIASES + " 를 읽지 못했어요", e);
        }
    }

    /**
     * @param sidoText    소스가 준 시도 이름 (없으면 null)
     * @param sigunguText 소스가 준 시군구 이름 (없으면 null)
     * @param address     주소 (시도·시군구를 못 얻었을 때 앞 단어에서 찾는다)
     * @param sidoCode    소스가 준 시도 코드 (없으면 null)
     * @param sigunguCode 소스가 준 시군구 코드 (없으면 null)
     */
    public Region resolve(String sidoText, String sigunguText, String address, String sidoCode, String sigunguCode,
                          NormalizationReport report) {
        if (nonDomestic.contains(Texts.blankToNull(sidoText))) {
            return Region.NONE;
        }
        List<String> tokens = tokens(address);

        String sido = null;
        if (sidoCode != null && sidoNameByCode.containsKey(sidoCode)) {
            sido = sidoCode;
        }
        if (sido == null) sido = findSido(sidoText);
        if (sido == null && !tokens.isEmpty()) sido = findSido(tokens.get(0));
        if (sido == null) {
            if (sidoText != null || sidoCode != null || !tokens.isEmpty()) {
                report.unmapped("region", firstNonBlank(sidoText, sidoCode, tokens.isEmpty() ? null : tokens.get(0)));
            }
            return Region.NONE;
        }

        Map<String, String> sigungus = sigunguByNameBySido.getOrDefault(sido, Map.of());
        String sigungu = null;
        if (sigunguCode != null && sigungus.containsValue(sigunguCode)) {
            sigungu = sigunguCode;
        }
        if (sigungu == null) sigungu = findSigungu(sido, sigungus, sigunguText);
        for (int i = 1; sigungu == null && i < Math.min(tokens.size(), 4); i++) {
            sigungu = findSigungu(sido, sigungus, tokens.get(i));
        }
        // 시군구를 못 찾았는데 이름 후보가 있었다면(신설 행정구역 등) 표를 고칠 수 있게 남긴다. 시군구가 없는 시도(세종)는 제외
        if (sigungu == null && !sigungus.isEmpty()) {
            String candidate = firstNonBlank(sigunguText, tokens.size() > 1 ? tokens.get(1) : null);
            // 읍·면·동·도로명이거나 시도 이름이 한 번 더 온 값(세종시)은 시군구 후보가 아니다
            if (candidate != null && NOT_SIGUNGU_SUFFIXES.stream().noneMatch(candidate::endsWith) && !sido.equals(findSido(candidate))) {
                report.unmapped("sigungu", sidoNameByCode.get(sido) + " " + candidate);
            }
        }
        return new Region(sido, sigungu);
    }

    /** 질의 속 지역 언급. matchedWords 는 질의에서 지역을 가리키는 낱말(조사 포함 원문) */
    public record Mention(String sidoCode, String sigunguCode, List<String> matchedWords) {
    }

    /**
     * 문장 속에서 시도·시군구 이름을 찾는다. 낱말이 지역 이름이거나 "지역 이름 + 조사"(서울에서, 강남구의)일 때만 인정한다.
     * 여러 시도에 있는 시군구 이름(중구·서구)은 시도가 함께 언급됐을 때만 쓰고, 그렇지 않으면 무시한다.
     *
     * @param particles 지역 이름 뒤에 붙을 수 있는 조사·말 (사전 파일에서 온다)
     */
    public Mention findIn(String text, Collection<String> particles) {
        if (text == null || text.isBlank()) return null;
        List<String> suffixes = particles.stream().sorted((a, b) -> b.length() - a.length()).toList();
        List<String> matched = new ArrayList<>();
        String sido = null;
        List<List<String[]>> sigunguHits = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            String word = region(token, suffixes);
            if (word == null) continue;
            if (sidoByWord.containsKey(word)) {
                if (sido == null) sido = sidoByWord.get(word);
                matched.add(token);
            } else {
                sigunguHits.add(sigunguByWord.get(word));
                matched.add(token);
            }
        }
        String sigungu = null;
        for (List<String[]> hit : sigunguHits) {
            String wanted = sido;
            List<String[]> candidates = wanted == null ? hit : hit.stream().filter(c -> c[0].equals(wanted)).toList();
            if (candidates.size() == 1) {
                if (sido == null) sido = candidates.get(0)[0];
                sigungu = candidates.get(0)[1];
                break;
            }
        }
        if (sido == null) return null;
        return new Mention(sido, sigungu, matched);
    }

    /** 낱말에서 조사를 떼어 지역 이름이면 그 이름을, 아니면 null */
    private String region(String token, List<String> suffixes) {
        if (isRegionWord(token)) return token;
        for (String suffix : suffixes) {
            if (token.length() > suffix.length() && token.endsWith(suffix)) {
                String word = token.substring(0, token.length() - suffix.length());
                if (isRegionWord(word)) return word;
            }
        }
        return null;
    }

    private boolean isRegionWord(String word) {
        return sidoByWord.containsKey(word) || sigunguByWord.containsKey(word);
    }

    /** 시도 목록: 코드 → 짧은 이름(서울, 경기…) (코드 순) */
    public Map<String, String> sidoOptions() {
        Map<String, String> result = new java.util.TreeMap<>();
        sidoNameByCode.forEach((code, name) -> result.put(code, stem(name, SIDO_SUFFIXES)));
        return result;
    }

    /** 사람이 읽는 지역 이름 (예: "서울특별시 종로구"). 코드를 모르면 빈 문자열 */
    public String displayName(String sidoCode, String sigunguCode) {
        if (sidoCode == null) return "";
        String sido = sidoNameByCode.getOrDefault(sidoCode, "");
        String sigungu = sigunguCode == null ? "" : sigunguNameByCode.getOrDefault(sidoCode, Map.of()).getOrDefault(sigunguCode, "");
        return (sido + " " + sigungu).trim();
    }

    /**
     * 소스가 같은 주소를 두 번 붙여 주는 경우를 한 번으로 줄인다. "서울특별시 종로구 평창30길 40 서울 종로구 평창30길 40" →
     * "서울특별시 종로구 평창30길 40". 뒤쪽이 시도 이름으로 시작하고 그 나머지가 앞쪽의 끝과 같을 때만 줄인다.
     */
    public String cleanAddress(String address) {
        List<String> tokens = tokens(address);
        for (int i = 2; i < tokens.size() - 1; i++) {
            if (findSido(tokens.get(i)) == null) continue;
            String head = String.join(" ", tokens.subList(0, i));
            String rest = String.join(" ", tokens.subList(i + 1, tokens.size()));
            if (head.endsWith(rest)) return head;
        }
        return address;
    }

    /** 화면에 보일 짧은 지역 이름 (예: "서울 강남구", "경기"). 코드를 모르면 빈 문자열 */
    public String shortName(String sidoCode, String sigunguCode) {
        if (sidoCode == null || !sidoNameByCode.containsKey(sidoCode)) return "";
        String sido = stem(sidoNameByCode.get(sidoCode), SIDO_SUFFIXES);
        String sigungu = sigunguCode == null ? "" : sigunguNameByCode.getOrDefault(sidoCode, Map.of()).getOrDefault(sigunguCode, "");
        return (sido + " " + sigungu).trim();
    }

    private String findSido(String text) {
        String name = Texts.blankToNull(text);
        if (name == null) return null;
        for (Map.Entry<String, String> e : sidoNameByCode.entrySet()) {
            if (e.getValue().equals(name)) return e.getKey();
        }
        if (sidoByAlias.containsKey(name)) return sidoByAlias.get(name);
        String stem = stem(name, SIDO_SUFFIXES);
        for (Map.Entry<String, String> e : sidoNameByCode.entrySet()) {
            if (stem(e.getValue(), SIDO_SUFFIXES).equals(stem)) return e.getKey();
        }
        return null;
    }

    private String findSigungu(String sido, Map<String, String> sigungus, String text) {
        String name = Texts.blankToNull(text);
        if (name == null) return null;
        name = sigunguRenames.getOrDefault(sido, Map.of()).getOrDefault(name, name);
        if (sigungus.containsKey(name)) return sigungus.get(name);
        String stem = stem(name, SIGUNGU_SUFFIXES);
        if (stem.length() < 2) return null;
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, String> e : sigungus.entrySet()) {
            if (stem(e.getKey(), SIGUNGU_SUFFIXES).equals(stem)) matches.add(e.getValue());
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    static String stem(String name, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (name.length() > suffix.length() && name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private static List<String> tokens(String address) {
        if (address == null || address.isBlank()) return List.of();
        return List.of(address.trim().split("\\s+"));
    }


    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
