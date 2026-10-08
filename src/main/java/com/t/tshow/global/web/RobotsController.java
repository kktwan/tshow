package com.t.tshow.global.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 검색 로봇 안내. 화면과 행사 페이지는 허용하고 API·AI 추천은 막는다(비용이 드는 호출이다) */
@Controller
public class RobotsController {

    private final SiteModelAdvice site;

    public RobotsController(SiteModelAdvice site) {
        this.site = site;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /api/
                Disallow: /recommend
                Disallow: /actuator

                Sitemap: %s/sitemap.xml
                """.formatted(site.site().baseUrl());
    }
}
