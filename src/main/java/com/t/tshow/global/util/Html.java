package com.t.tshow.global.util;

import java.util.regex.Pattern;

/** HTML 도우미 */
public final class Html {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    /** 줄을 바꾸는 태그 (<br>, 문단·목록·제목·표의 끝) */
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</(p|div|li|ul|ol|h[1-6]|tr|table|blockquote)\\s*>");
    /**
     * 태그. 영문자나 / 로 시작하는 것만 태그로 본다 — 제목·설명에 글자로 쓰인 꺾쇠(<다담>, 1 < 2)는 지우지 않는다.
     */
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern LINE_EDGES = Pattern.compile("(?m)^[ \\t]+|[ \\t]+$");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private Html() {
    }

    /**
     * HTML 이 섞인 글을 보통 글로 바꾼다: 주석·태그를 지우고 문단 끝·줄바꿈 태그는 줄바꿈으로, 연속 공백과 빈 줄은 줄인다.
     * HTML 이 없는 글은 (앞뒤 공백과 연속 공백 정리 외에) 그대로다. 엔티티(&amp; 등)는 여기서 풀지 않는다.
     */
    public static String toText(String html) {
        if (html == null) return null;
        String text = COMMENT.matcher(html).replaceAll("");
        text = BREAK.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = SPACES.matcher(text).replaceAll(" ");
        text = LINE_EDGES.matcher(text).replaceAll("");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }
}
