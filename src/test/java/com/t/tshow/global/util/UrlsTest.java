package com.t.tshow.global.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlsTest {

    @Test
    void 엔티티로_온_앰퍼샌드를_돌려_링크의_조건이_깨지지_않게_한다() {
        // 운영에서 실제로 깨져 있던 링크의 모양
        assertEquals("https://wonju.moonhwain.kr:447/rsvc/rsv_pm.html?b_id=wonju&p_idx=260",
                Urls.safeHttp("https://wonju.moonhwain.kr:447/rsvc/rsv_pm.html?b_id=wonju&amp;p_idx=260"));
        assertEquals("http://x.kr/a?b=1&c=2", Urls.safeHttp("  http://x.kr/a?b=1&amp;amp;c=2 "));
    }

    @Test
    void http_https_가_아니거나_공백이_섞인_값은_버린다() {
        assertNull(Urls.safeHttp("javascript:alert(1)"));
        assertNull(Urls.safeHttp("www.museum.go.kr"));
        assertNull(Urls.safeHttp("http://x.kr/a b"));
        assertNull(Urls.safeHttp("   "));
        assertNull(Urls.safeHttp(null));
        assertEquals("HTTPS://X.KR", Urls.safeHttp("HTTPS://X.KR"));
    }
}
