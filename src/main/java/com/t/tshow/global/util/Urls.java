package com.t.tshow.global.util;

import java.util.regex.Pattern;

/** 주소(URL) 도우미 */
public final class Urls {

    private static final Pattern HTTP = Pattern.compile("(?i)^https?://[^\\s<>\"']+$");

    private Urls() {
    }

    /**
     * 소스가 준 링크를 화면에 쓸 수 있는 http(s) 주소로 다듬는다. 엔티티로 온 &amp; 를 & 로 돌리고(안 그러면 링크의 조건이 깨진다),
     * http·https 가 아닌 것(javascript: 등)과 공백이 섞인 값은 버린다. 쓸 수 없으면 null.
     */
    public static String safeHttp(String url) {
        if (url == null) return null;
        String cleaned = Html.unescape(url.trim());
        return HTTP.matcher(cleaned).matches() ? cleaned : null;
    }
}
