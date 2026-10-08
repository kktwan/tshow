package com.t.tshow.infra.source.tourapi;

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

class TourApiEventSourceTest {

    private TourApiEventSource source(FixtureHttp http, int lookbackDays) {
        IngestProperties base = TestProperties.ingest();
        IngestProperties.Source s = base.tourapi();
        IngestProperties props = new IngestProperties(base.horizonMonths(), base.refetchAfterHours(), false, base.cron(), base.zone(), base.appName(),
                base.sources(), base.dateFormats(), true, base.kopis(), base.culture(),
                new IngestProperties.Source(s.baseUrl(), s.windowDays(), s.pageSize(), 0, 0, 0, 5, lookbackDays));
        return new TourApiEventSource(props, new DateParser(props), "test-key", http);
    }

    private FixtureHttp http() {
        return new FixtureHttp().on("/searchFestival2", "tourapi/list.json")
                .on("/detailCommon2", "tourapi/common.json").on("/detailIntro2", "tourapi/intro.json");
    }

    @Test
    void 목록_공통상세_소개상세를_합쳐_RawEvent_로_바꾼다() {
        CollectingContext ctx = new CollectingContext();
        source(http(), 180).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), ctx);

        assertEquals(1, ctx.events.size(), "이미 끝난 행사는 제외된다");
        RawEvent e = ctx.events.get(0);
        assertEquals(SourceType.TOURAPI, e.source());
        assertEquals("02-3435-1000", e.phone(), "문의 전화");
        assertEquals("3379778", e.sourceId());
        assertEquals("가락 옥토버페스트", e.title());
        assertEquals("EV010600", e.sourceCategory());
        assertEquals(LocalDate.of(2026, 10, 16), e.startDate());
        assertEquals(LocalDate.of(2026, 10, 18), e.endDate());
        assertEquals("가락몰 3층 하늘공원", e.venueName());
        assertEquals("서울특별시 송파구 양재대로 932 (가락동), 가락몰 3층 하늘공원", e.address());
        assertEquals("11", e.sidoCode());
        assertEquals("710", e.sigunguCode());
        assertEquals(37.4960246566, e.lat(), 1e-9, "위도는 mapy");
        assertEquals(127.1107532009, e.lon(), 1e-9, "경도는 mapx");
        assertEquals("무료", e.priceText());
        assertEquals("16:00~22:00", e.scheduleText());
        assertEquals("서울시농수산식품공사", e.hostText());
        assertTrue(e.description().contains("가락시장"));
        assertEquals("https://www.garak.co.kr/", e.infoUrl(), "homepage 의 HTML 에서 주소만 뽑는다");
        assertTrue(e.imageUrl().endsWith("image3_1.png"), "목록용 작은 이미지를 쓴다");
        assertEquals("Type3", e.imageLicense());
        assertNotNull(e.sourceUpdatedAt());
    }

    @Test
    void 진행_중인_행사를_얻으려고_과거부터_조회한다() {
        FixtureHttp http = http();
        source(http, 180).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), new CollectingContext());
        // 2026-10-08 - 180일 = 2026-04-11
        assertTrue(http.urls.get(0).contains("eventStartDate=20260411"), http.urls.get(0));
    }

    @Test
    void 최근에_받은_항목은_상세를_다시_조회하지_않는다() {
        FixtureHttp http = new FixtureHttp().on("/searchFestival2", "tourapi/list.json");
        CollectingContext ctx = new CollectingContext();
        ctx.fresh = true;
        source(http, 180).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), ctx);
        assertEquals(1, ctx.skipped);
        assertTrue(http.urls.stream().noneMatch(u -> u.contains("/detail")));
    }

    @Test
    void 오류_응답은_실패로_다룬다() {
        FixtureHttp http = new FixtureHttp().on("/searchFestival2",
                "inline:{\"response\":{\"header\":{\"resultCode\":\"0030\",\"resultMsg\":\"SERVICE KEY IS NOT REGISTERED\"}}}");
        assertThrows(IllegalStateException.class,
                () -> source(http, 0).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), new CollectingContext()));
    }
}
