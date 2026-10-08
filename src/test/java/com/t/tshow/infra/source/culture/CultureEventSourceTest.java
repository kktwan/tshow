package com.t.tshow.infra.source.culture;

import com.t.tshow.domain.ingest.dto.RawEvent;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.service.normalize.DateParser;
import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.infra.source.CollectingContext;
import com.t.tshow.infra.source.FixtureHttp;
import com.t.tshow.support.TestProperties;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CultureEventSourceTest {

    private CultureEventSource source(FixtureHttp http) {
        IngestProperties props = TestProperties.ingest();
        return new CultureEventSource(props, new DateParser(props), "test-key", http);
    }

    @Test
    void 목록과_상세를_합쳐_RawEvent_로_바꾼다() {
        FixtureHttp http = new FixtureHttp().on("/period2", "culture/list.xml").on("/detail2", "culture/detail.xml");
        CollectingContext ctx = new CollectingContext();
        source(http).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), ctx);

        assertEquals(1, ctx.events.size());
        RawEvent e = ctx.events.get(0);
        assertEquals(SourceType.CULTURE, e.source());
        assertEquals("371407", e.sourceId());
        assertEquals("전시", e.sourceCategory());
        assertEquals(LocalDate.of(2025, 11, 22), e.startDate());
        assertEquals(LocalDate.of(2026, 10, 11), e.endDate());
        assertEquals("국립중앙박물관", e.venueName());
        assertEquals("서울특별시 용산구 서빙고로 137 국립중앙박물관", e.address());
        assertEquals("서울", e.sidoText());
        assertEquals("용산구", e.sigunguText());
        assertEquals(37.524063, e.lat(), 1e-6, "위도는 gpsY");
        assertEquals(126.980236, e.lon(), 1e-6, "경도는 gpsX");
        assertEquals("무료", e.priceText());
        assertNull(e.description(), "설명이 비어 있으면 null");
        assertTrue(e.imageUrl().endsWith("full.jpg"), "상세의 원본 이미지를 우선한다");
        assertTrue(e.infoUrl().startsWith("https://culture.seoul.go.kr"));
    }

    @Test
    void 조회_구간_길이는_설정을_따른다() {
        IngestProperties base = TestProperties.ingest();
        IngestProperties.Source s = base.culture();
        // 운영 설정처럼 기간 한도가 없는 값(400일)이면 6개월 범위가 한 구간이다
        IngestProperties props = new IngestProperties(base.horizonMonths(), base.refetchAfterHours(), false, base.cron(), base.zone(), base.appName(),
                base.sources(), base.dateFormats(), true, base.kopis(),
                new IngestProperties.Source(s.baseUrl(), 400, s.pageSize(), 0, 0, 0, 5, 0), base.tourapi());
        FixtureHttp wide = new FixtureHttp().on("/period2", "culture/list.xml").on("/detail2", "culture/detail.xml");
        new CultureEventSource(props, new DateParser(props), "test-key", wide)
                .fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), new CollectingContext());
        assertEquals(1, wide.urls.stream().filter(u -> u.contains("/period2")).count());

        // 30일 단위 설정이면 같은 범위가 여러 구간으로 나뉜다
        FixtureHttp narrow = new FixtureHttp().on("/period2", "culture/list.xml").on("/detail2", "culture/detail.xml");
        source(narrow).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), new CollectingContext());
        assertTrue(narrow.urls.stream().filter(u -> u.contains("/period2")).count() > 1);
    }

    @Test
    void 최근에_받은_항목은_상세를_다시_조회하지_않는다() {
        FixtureHttp http = new FixtureHttp().on("/period2", "culture/list.xml");
        CollectingContext ctx = new CollectingContext();
        ctx.fresh = true;
        source(http).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), ctx);
        assertEquals(1, ctx.skipped);
        assertTrue(http.urls.stream().noneMatch(u -> u.contains("/detail2")));
    }
}
