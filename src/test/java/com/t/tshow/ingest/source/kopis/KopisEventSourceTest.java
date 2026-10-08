package com.t.tshow.ingest.source.kopis;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.ingest.TestProperties;
import com.t.tshow.ingest.http.HttpFetcher;
import com.t.tshow.ingest.normalize.DateParser;
import com.t.tshow.ingest.source.IngestContext;
import com.t.tshow.ingest.source.RawEvent;
import com.t.tshow.ingest.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class KopisEventSourceTest {

    /** 실제 KOPIS 응답을 저장해 둔 가짜 HTTP: 첫 쪽에만 공연이 있고 나머지 쪽·기간은 비어 있다 */
    private static class FakeHttp implements HttpFetcher {
        final List<String> urls = new ArrayList<>();

        @Override
        public String get(String url, String label) {
            urls.add(url);
            if (url.contains("/pblprfr?")) {
                return url.contains("stdate=20261008") && url.contains("cpage=1&") ? read("list-page1.xml") : "<dbs></dbs>";
            }
            if (url.contains("/pblprfr/PF302763")) return read("detail.xml");
            if (url.contains("/prfplc/FC005066")) return read("facility.xml");
            throw new IllegalStateException("예상하지 못한 호출: " + label);
        }

        private static String read(String name) {
            try {
                return new String(new ClassPathResource("kopis/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class Collector implements IngestContext {
        final List<RawEvent> events = new ArrayList<>();
        int skipped;
        boolean fresh;

        @Override public boolean isFresh(SourceType source, String sourceId) { return fresh; }
        @Override public void emit(RawEvent event) { events.add(event); }
        @Override public void skipped() { skipped++; }
        @Override public void failed(SourceType source, String sourceId, Exception cause) { fail(cause); }
    }

    private KopisEventSource source(FakeHttp http) {
        IngestProperties props = TestProperties.ingest();
        return new KopisEventSource(props, new DateParser(props), "test-key", http);
    }

    @Test
    void 목록_상세_공연장을_합쳐_RawEvent_로_바꾼다() {
        FakeHttp http = new FakeHttp();
        Collector ctx = new Collector();
        source(http).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20), ctx);

        assertEquals(1, ctx.events.size());
        RawEvent e = ctx.events.get(0);
        assertEquals(SourceType.KOPIS, e.source());
        assertEquals("PF302763", e.sourceId());
        assertEquals("서양음악(클래식)", e.sourceCategory());
        assertEquals(LocalDate.of(2026, 10, 28), e.startDate());
        assertEquals("시흥아트센터", e.venueName());
        assertEquals("경기도 시흥시 서울대학로 255 (배곧동)", e.address());
        assertEquals("경기도", e.sidoText());
        assertEquals(37.369613, e.lat(), 1e-6);
        assertEquals(126.72345, e.lon(), 1e-6);
        assertEquals("R석 20,000원, S석 10,000원", e.priceText());
        assertEquals("만 7세 이상", e.ageText());
        assertNull(e.description(), "줄거리가 공백이면 null");
        assertEquals(1, e.ticketLinks().size());
        assertEquals("놀유니버스", e.ticketLinks().get(0).name());
        assertNotNull(e.sourceUpdatedAt());
    }

    @Test
    void 조회_기간은_한도를_넘지_않게_나눠서_호출한다() {
        FakeHttp http = new FakeHttp();
        source(http).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2027, 4, 8), new Collector());

        Pattern p = Pattern.compile("stdate=(\\d{8})&eddate=(\\d{8})");
        int windows = 0;
        for (String url : http.urls) {
            Matcher m = p.matcher(url);
            if (!m.find()) continue;
            LocalDate s = LocalDate.parse(m.group(1), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            LocalDate e = LocalDate.parse(m.group(2), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            assertTrue(ChronoUnit.DAYS.between(s, e) <= 30, "기간 한도(30일) 초과: " + s + "~" + e);
            windows++;
        }
        assertTrue(windows >= 6, "6개월은 여러 구간으로 나뉘어야 해요: " + windows);
    }

    @Test
    void 최근에_받은_항목은_상세를_다시_조회하지_않는다() {
        FakeHttp http = new FakeHttp();
        Collector ctx = new Collector();
        ctx.fresh = true;
        source(http).fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20), ctx);

        assertEquals(0, ctx.events.size());
        assertEquals(1, ctx.skipped);
        assertTrue(http.urls.stream().noneMatch(u -> u.contains("/pblprfr/PF")), "상세를 조회하면 안 돼요");
    }

    @Test
    void 키가_없으면_수집을_시작하지_않는다() {
        IngestProperties props = TestProperties.ingest();
        KopisEventSource noKey = new KopisEventSource(props, new DateParser(props), " ", new FakeHttp());
        assertThrows(IllegalStateException.class,
                () -> noKey.fetch(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 20), new Collector()));
    }
}
