package com.t.tshow.domain.event.controller;

import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.global.web.SiteModelAdvice;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 검색엔진용 사이트맵(/sitemap.xml): 첫 화면과 논리삭제되지 않은 행사 상세 주소 전부.
 * 사이트맵 하나에는 5만 개까지만 담을 수 있다. 호출마다 DB 를 읽지 않게 한 시간 동안 만들어 둔 것을 쓴다.
 */
@Controller
public class SitemapController {

    private static final int MAX_URLS = 50_000;
    private static final Duration KEEP = Duration.ofHours(1);

    private final EventRepository events;
    private final SiteModelAdvice site;
    private final Clock clock;
    private String cached;
    private Instant cachedAt = Instant.MIN;

    public SitemapController(EventRepository events, SiteModelAdvice site, Clock clock) {
        this.events = events;
        this.site = site;
        this.clock = clock;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public synchronized String sitemap() {
        Instant now = clock.instant();
        if (cached == null || cachedAt.plus(KEEP).isBefore(now)) {
            cached = build();
            cachedAt = now;
        }
        return cached;
    }

    private String build() {
        String base = site.site().baseUrl();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                .append("  <url><loc>").append(base).append("/</loc></url>\n");
        for (EventRepository.SitemapRow row : events.findSitemapRows(PageRequest.of(0, MAX_URLS - 1))) {
            xml.append("  <url><loc>").append(base).append("/events/").append(row.getId()).append("</loc><lastmod>")
                    .append(DateTimeFormatter.ISO_LOCAL_DATE.format(row.getUpdatedAt().atZone(ZoneOffset.UTC))).append("</lastmod></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }
}
