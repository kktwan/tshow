package com.t.tshow.domain.search.service;

import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.search.dto.SearchQuery;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.global.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryAnalyzerTest {

    /** 2026-10-08 목요일 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private final RegionResolver regions = new RegionResolver();
    private final CategoryResolver categories = new CategoryResolver();
    private final SearchProperties properties = new SearchProperties(60, 0.3, 12, 48, 100, 5, List.of(1, 3, 5, 10),
            List.of("today"), "Asia/Seoul", Map.of());
    private final QueryAnalyzer analyzer = new QueryAnalyzer(new SearchDictionary(categories), regions, properties);

    private static SearchRequest request(String q) {
        return new SearchRequest(q, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void 날짜_지역_무료_분류를_조건으로_뽑고_문장은_비운다() {
        SearchQuery q = analyzer.analyze(request("이번 주말 서울 무료 전시"), TODAY);

        assertEquals("", q.text(), "조건만 있는 검색은 의미 검색을 하지 않는다");
        assertFalse(q.semantic());
        assertEquals("11", q.sido());
        assertTrue(q.free());
        assertEquals(List.of("exhibition"), q.categories());
        assertEquals(LocalDate.of(2026, 10, 10), q.from());
        assertEquals(LocalDate.of(2026, 10, 11), q.to());
        assertEquals("weekend", q.dateKey());
    }

    @Test
    void 조건을_뺀_나머지는_의미_검색_문장으로_남는다() {
        SearchQuery q = analyzer.analyze(request("아이와 갈 만한 공연"), TODAY);

        assertEquals("아이와", q.text());
        assertTrue(q.semantic());
        assertEquals(List.of("PERFORMANCE"), q.kinds());
        assertNull(q.sido());
        assertEquals(TODAY, q.from(), "날짜 표현이 없으면 오늘 이후의 행사");
        assertNull(q.to());
    }

    @Test
    void 지역_뒤에_조사가_붙어도_지역으로_본다() {
        SearchQuery q = analyzer.analyze(request("부산에서 열리는 축제"), TODAY);
        assertEquals("26", q.sido());
        assertEquals(List.of("festival"), q.categories());
        assertEquals("", q.text());
    }

    @Test
    void 시군구는_이름이_하나뿐이면_시도까지_함께_정한다() {
        SearchQuery q = analyzer.analyze(request("강남구 클래식 연주회"), TODAY);
        assertEquals("11", q.sido());
        assertNotNull(q.sigungu());
        assertEquals("서울 강남구", regions.shortName(q.sido(), q.sigungu()));
        assertEquals(List.of("classical"), q.categories());
        assertEquals("연주회", q.text());
    }

    @Test
    void 여러_시도에_있는_시군구_이름은_시도가_없으면_지역으로_보지_않는다() {
        SearchQuery q = analyzer.analyze(request("중구 전시"), TODAY);
        assertNull(q.sido(), "서울 중구인지 부산 중구인지 알 수 없다");
        assertEquals("중구", q.text());

        SearchQuery withSido = analyzer.analyze(request("부산 중구 전시"), TODAY);
        assertEquals("26", withSido.sido());
        assertNotNull(withSido.sigungu());
    }

    @Test
    void 화면에서_고른_조건은_검색어_속_표현보다_우선한다() {
        SearchRequest r = new SearchRequest("서울 전시 이번 주말", "PERFORMANCE", "theater", "26", null, "today", null, null,
                null, null, null, null, null);
        SearchQuery q = analyzer.analyze(r, TODAY);

        assertEquals("26", q.sido(), "서울이라고 적었어도 화면에서 고른 부산이 우선");
        assertEquals(List.of("theater"), q.categories());
        assertEquals(List.of("PERFORMANCE"), q.kinds());
        assertEquals(TODAY, q.from());
        assertEquals(TODAY, q.to(), "화면에서 고른 '오늘' 이 우선");
        assertEquals("", q.text(), "검색어 속 표현은 조건으로 쓰이지 않아도 문장에서는 빠진다");
    }

    @Test
    void 특정_날짜는_지난_날이면_오늘로_맞춘다() {
        SearchRequest r = new SearchRequest("", null, null, null, null, null, LocalDate.of(2026, 10, 1), null, null, null, null, null, null);
        SearchQuery q = analyzer.analyze(r, TODAY);
        assertEquals(TODAY, q.from());
        assertEquals(TODAY, q.to());
    }

    @Test
    void 내_주변은_반경을_정하고_없으면_기본_반경을_쓴다() {
        SearchRequest r = new SearchRequest(null, null, null, null, null, null, null, null, 37.5665, 126.978, 3, null, null);
        SearchQuery.Near near = analyzer.analyze(r, TODAY).near();
        assertEquals(3000, near.radiusMeters());

        SearchRequest defaults = new SearchRequest(null, null, null, null, null, null, null, null, 37.5665, 126.978, null, null, null);
        assertEquals(5000, analyzer.analyze(defaults, TODAY).near().radiusMeters());

        SearchRequest bad = new SearchRequest(null, null, null, null, null, null, null, null, 137.0, 126.978, null, null, null);
        assertNull(analyzer.analyze(bad, TODAY).near(), "범위를 벗어난 좌표는 무시한다");
    }

    @Test
    void 짧은_군더더기_낱말은_다른_낱말_속에서_지워지지_않는다() {
        SearchQuery q = analyzer.analyze(request("거리 공연 곳곳에서 하는 퍼레이드"), TODAY);
        assertTrue(q.text().contains("거리"), "'거' 가 '거리' 에서 지워지면 안 된다: " + q.text());
        assertTrue(q.text().contains("곳곳에서"));
        assertTrue(q.text().contains("퍼레이드"));
    }

    @Test
    void 의미_없는_한_글자만_남으면_문장으로_보지_않는다() {
        assertFalse(analyzer.analyze(request("서울 무료 전시 좀"), TODAY).semantic());
        assertFalse(analyzer.analyze(request("   "), TODAY).semantic());
        assertFalse(analyzer.analyze(request(null), TODAY).semantic());
    }
}
