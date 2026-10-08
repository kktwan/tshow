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
    }
}
