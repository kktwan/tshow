package com.t.tshow.global.util;

import java.util.regex.Pattern;

/** 여러 곳에서 쓰는 문자열 도우미 */
public final class Texts {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Texts() {
    }

    /** null 이 아니고 공백만이 아니면 true. 문자열이 아닌 값은 null 이 아니면 true */
    public static boolean has(Object value) {
        return value != null && !(value instanceof CharSequence s && s.toString().isBlank());
    }

    /** 앞뒤 공백을 지우고, 비었으면 null */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 연속 공백·줄바꿈을 한 칸으로 줄이고 앞뒤를 자른다. null 은 빈 문자열 */
    public static String collapseSpaces(String s) {
        return s == null ? "" : SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** 앞에서부터 처음으로 값이 있는 것. 모두 없으면 null */
    public static String firstOf(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
