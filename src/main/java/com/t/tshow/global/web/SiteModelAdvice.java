package com.t.tshow.global.web;

import com.t.tshow.domain.ingest.service.DataFreshnessService;
import com.t.tshow.global.config.SiteProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 화면 컨트롤러의 모든 모델에 하단 정보(site)를 넣는다 */
@ControllerAdvice(annotations = Controller.class)
public class SiteModelAdvice {

    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("M.d (E) HH:mm", Locale.KOREAN);

    private final SiteProperties properties;
    private final DataFreshnessService freshness;
    private final Clock clock;

    public SiteModelAdvice(SiteProperties properties, DataFreshnessService freshness, Clock clock) {
        this.properties = properties;
        this.freshness = freshness;
        this.clock = clock;
    }

    @ModelAttribute("site")
    public SiteInfo site() {
        Instant updated = freshness.lastUpdatedAt();
        String label = updated == null ? null : UPDATED.format(updated.atZone(clock.getZone()));
        String base = properties.baseUrl().trim();
        return new SiteInfo(properties.contactEmail().trim(), label, LocalDate.now(clock).getYear(),
                base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
    }
}
