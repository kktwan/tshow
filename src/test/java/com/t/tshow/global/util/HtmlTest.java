package com.t.tshow.global.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlTest {

    @Test
    void 주석과_태그를_지우고_문단은_줄바꿈으로_바꾼다() {
        String html = "<!-- wp:paragraph -->\n<p><strong>2026</strong><strong>년 8월 20일</strong></p>\n<!-- /wp:paragraph -->\n\n"
                + "<!-- wp:paragraph -->\n<p>차계남의 작업은 늘 재료에서 출발했다.</p>\n<!-- /wp:paragraph -->";
        assertEquals("2026년 8월 20일\n\n차계남의 작업은 늘 재료에서 출발했다.", Html.toText(html));
    }

    @Test
    void br_과_목록은_줄바꿈이고_연속_공백과_빈_줄은_줄인다() {
        assertEquals("1회\n2회\n\n3회", Html.toText("1회<br>2회<BR/>\n\n\n\n<div>3회</div>"));
        assertEquals("가 나", Html.toText("가     나"));
    }

    @Test
    void 글자로_쓴_꺾쇠는_지우지_않는다() {
        assertEquals("<다담> 공연 1 < 2", Html.toText("<다담> 공연 1 < 2"));
    }

    @Test
    void HTML_이_없는_글과_null_은_그대로다() {
        assertEquals("그냥 글입니다.\n둘째 줄", Html.toText("  그냥 글입니다.\n둘째 줄 "));
        assertNull(Html.toText(null));
        assertNull(Html.toPlainText(null));
    }

    @Test
    void 워드프레스_블록과_이미지_태그를_걷고_엔티티로_온_꺾쇠는_글자로_남긴다() {
        // 운영에서 실제로 깨져 보였던 전시 소개글의 모양
        String raw = "<!-- wp:image {\"id\":20284,\"sizeSlug\":\"full\"} -->\n"
                + "<figure class=\"wp-block-image size-full\"><img src=\"http://x.kr/a.jpg\" alt=\"\" class=\"wp-image-20284\" />\n</figure>\n"
                + "<!-- /wp:image -->\n\n"
                + "<!-- wp:paragraph -->\n<p>故전국광의 조각 &lt;매스의 내면&gt;(1987)은 독자적인 조형 언어를 보여준다.</p>\n<!-- /wp:paragraph -->";
        assertEquals("故전국광의 조각 <매스의 내면>(1987)은 독자적인 조형 언어를 보여준다.", Html.toPlainText(raw));
    }

    @Test
    void 태그가_엔티티로_온_것도_태그로_돌려_걷고_같은_값에_다시_적용해도_같다() {
        String once = Html.toPlainText("&lt;p&gt;첫 문단&lt;/p&gt;&lt;p&gt;둘째 &amp;amp; 문단&lt;/p&gt;");
        assertEquals("첫 문단\n둘째 & 문단", once);
        assertEquals(once, Html.toPlainText(once), "수집할 때와 병합할 때 두 번 적용해도 안전하다");
        assertEquals("A & B", Html.unescape("A &amp;amp; B"), "두 번 감싸진 것도 푼다");
    }
}
