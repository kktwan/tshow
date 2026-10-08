package com.t.tshow.global.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/** HTML 도우미 */
public final class Html {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    /** 줄을 바꾸는 태그 (<br>, 문단·목록·제목·표의 끝) */
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>|</(p|div|li|ul|ol|h[1-6]|tr|table|blockquote)\\s*>");
    /**
     * 태그. 실제 HTML 태그 이름일 때만 태그로 본다 — 제목·설명에 글자로 쓰인 꺾쇠(<다담>, <GRACE>, 1 < 2)는 지우지 않는다.
     * 이름 뒤는 공백·/·> 여야 한다(<p> 는 맞고 <pre> 의 앞부분 p 로는 맞지 않는다).
     */
    private static final Pattern TAG = Pattern.compile("(?i)</?(?:a|abbr|article|aside|b|blockquote|br|button|center|code|dd|del|div|dl|dt|em|figcaption|figure|font|footer|form|h[1-6]|header|hr|i|iframe|img|input|ins|label|li|main|mark|nav|noscript|ol|p|path|picture|pre|s|script|section|small|source|span|strike|strong|style|sub|sup|svg|table|tbody|td|tfoot|th|thead|tr|u|ul|video)(?=[\\s/>])[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern LINE_EDGES = Pattern.compile("(?m)^[ \\t]+|[ \\t]+$");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private Html() {
    }

    /**
     * 글자가 HTML 엔티티로 온 경우(&amp; &lt; &#39; 등, 두 번 감싸져 오기도 한다)를 원래 글자로 바꾼다.
     * 더 바뀌지 않을 때까지 (최대 3번) 해제한다.
     */
    public static String unescape(String text) {
        if (text == null) return null;
        String current = text;
        for (int i = 0; i < 3; i++) {
            String next = HtmlUtils.htmlUnescape(current);
            if (next.equals(current)) break;
            current = next;
        }
        return current;
    }

    /**
     * 소개글 같은 본문을 화면에 보일 보통 글로 만든다: 엔티티를 풀고(&lt;p&gt; 처럼 태그가 엔티티로 온 것도 태그로 돌아온다) 태그를 걷는다.
     * 같은 값에 다시 적용해도 결과가 같다(수집할 때와 병합할 때 모두 적용해도 안전하다).
     */
    public static String toPlainText(String value) {
        return toText(unescape(value));
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
