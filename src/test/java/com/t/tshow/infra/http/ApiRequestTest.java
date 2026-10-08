package com.t.tshow.infra.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiRequestTest {

    @Test
    void 파라미터는_넣은_순서대로_인코딩해서_붙인다() {
        ApiRequest request = ApiRequest.to("http://api.test", "/list")
                .secret("serviceKey", "a+b/c=").param("title", "어린 왕자").param("page", 2);
        assertEquals("http://api.test/list?serviceKey=a%2Bb%2Fc%3D&title=%EC%96%B4%EB%A6%B0+%EC%99%95%EC%9E%90&page=2", request.url());
    }

    @Test
    void 이미_인코딩된_인증키는_다시_인코딩하지_않는다() {
        assertEquals("http://api.test/x?key=a%2Bb%3D", ApiRequest.to("http://api.test", "/x").secret("key", "a%2Bb%3D").url());
    }

    @Test
    void 파라미터가_없으면_물음표를_붙이지_않고_label_은_주소와_따로_둔다() {
        ApiRequest request = ApiRequest.to("http://api.test", "/detail").label("상세 1");
        assertEquals("http://api.test/detail", request.url());
        assertEquals("상세 1", request.label());
    }
}
