package com.t.tshow.global.web;

import com.t.tshow.domain.event.controller.SitemapController;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.ingest.service.DataFreshnessService;
import com.t.tshow.global.config.SiteProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeoControllersTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final SiteModelAdvice site = new SiteModelAdvice(new SiteProperties("", "https://example.test/"),
            Mockito.mock(DataFreshnessService.class), clock);

    @Test
    void robots_는_API_와_AI_추천을_막고_사이트맵_주소를_알려_준다() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RobotsController(site)).build();
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disallow: /api/")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disallow: /recommend")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sitemap: https://example.test/sitemap.xml")));
    }

    @Test
    void 사이트맵은_첫_화면과_행사_상세_주소를_담는다() throws Exception {
        EventRepository events = Mockito.mock(EventRepository.class);
        UUID id = UUID.randomUUID();
        EventRepository.SitemapRow row = new EventRepository.SitemapRow() {
            @Override public UUID getId() { return id; }
            @Override public Instant getUpdatedAt() { return Instant.parse("2026-10-07T20:00:00Z"); }
        };
        when(events.findSitemapRows(any())).thenReturn(List.of(row));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SitemapController(events, site, clock)).build();

        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<loc>https://example.test/</loc>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<loc>https://example.test/events/" + id + "</loc>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<lastmod>2026-10-07</lastmod>")));
    }
}
