package com.t.tshow.ingest.normalize;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    public RegionResolver() {
        loadCsv();
        loadAliases();
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
        if (nonDomestic.contains(blankToNull(sidoText))) {
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

    /** 사람이 읽는 지역 이름 (예: "서울특별시 종로구"). 코드를 모르면 빈 문자열 */
    public String displayName(String sidoCode, String sigunguCode) {
        if (sidoCode == null) return "";
        String sido = sidoNameByCode.getOrDefault(sidoCode, "");
        String sigungu = sigunguCode == null ? "" : sigunguNameByCode.getOrDefault(sidoCode, Map.of()).getOrDefault(sigunguCode, "");
        return (sido + " " + sigungu).trim();
    }

    private String findSido(String text) {
        String name = blankToNull(text);
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
        String name = blankToNull(text);
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
