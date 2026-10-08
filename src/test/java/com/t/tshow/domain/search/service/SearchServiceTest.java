package com.t.tshow.domain.search.service;

import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.event.service.EventPresenter;
import com.t.tshow.domain.index.port.Embedder;
import com.t.tshow.domain.index.port.VectorIndex;
import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.global.config.SearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 외부(Qdrant·OpenAI·DB)는 가짜로 두고, 조건이 먼저 걸리는지·관련도 하한·대체 동작을 확인한다 */
class SearchServiceTest {

    /** 2026-10-08 목요일 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static class FakeIndex implements VectorIndex {
        List<Hit> hits = List.of();
        Filter lastFilter;
        int searches;

        @Override public void ensureCollection() { }
        @Override public void upsert(List<Point> points) { }
        @Override public void overwritePayload(UUID id, Map<String, Object> payload) { }
        @Override public void delete(Collection<UUID> ids) { }
        @Override public Set<UUID> allIds() { return Set.of(); }
        @Override public List<Hit> search(float[] vector, Filter filter, int limit) {
            searches++;
            lastFilter = filter;
            return hits;
        }
    }

    private static class FakeEmbedder implements Embedder {
        boolean configured = true;
        @Override public boolean isConfigured() { return configured; }
        @Override public List<float[]> embed(List<String> texts) { return texts.stream().map(t -> new float[]{1, 0}).toList(); }
    }

    private final FakeIndex index = new FakeIndex();
    private final FakeEmbedder embedder = new FakeEmbedder();
    private final EventRepository events = Mockito.mock(EventRepository.class);
    private SearchService service;

    @BeforeEach
    void setUp() {
        CategoryResolver categories = new CategoryResolver();
        RegionResolver regions = new RegionResolver();
        SearchProperties properties = new SearchProperties(60, 0.3, 12, 48, 100, 5, List.of(1, 3, 5, 10), List.of("today"), List.of(),
                "Asia/Seoul", Map.of());
        SearchDictionary dictionary = new SearchDictionary(categories);
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        service = new SearchService(new QueryAnalyzer(dictionary, regions, properties), dictionary, index, embedder, events,
                new EventPresenter(), categories, regions, properties, clock);
    }

    private static SearchRequest request(String q) {
        return new SearchRequest(q, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Event event(String title, Double lat, Double lon) {
        return Event.builder().id(UUID.randomUUID()).kind("PERFORMANCE").category("theater").title(title).titleNorm(title)
                .startDate(TODAY.plusDays(2)).endDate(TODAY.plusDays(2)).sidoCode("11").sigunguCode("680").lat(lat).lon(lon)
                .priceType("FREE").sourceCount(1).dataHash("h").createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 조건은_벡터_검색_전에_필터로_걸리고_문장만_의미_검색에_쓴다() {
        Event a = event("어린이 연극", null, null);
        index.hits = List.of(new VectorIndex.Hit(a.getId(), 0.5));
        when(events.findAllById(any())).thenReturn(List.of(a));

        SearchResponse r = service.search(request("이번 주말 서울 무료 아이와 갈 만한 공연"));

        assertEquals(1, index.searches);
        VectorIndex.Filter f = index.lastFilter;
        assertEquals(LocalDate.of(2026, 10, 10).toEpochDay(), f.endDayAtLeast(), "이번 주말 시작");
        assertEquals(LocalDate.of(2026, 10, 11).toEpochDay(), f.startDayAtMost(), "이번 주말 끝");
        assertEquals("11", f.sido());
        assertEquals("FREE", f.priceType());
        assertEquals(List.of("PERFORMANCE"), f.kinds());
        assertEquals("아이와", r.query());
        assertTrue(r.semantic());
        assertEquals(List.of("서울", "이번 주말", "무료", "공연"), r.interpreted());
        assertEquals(1, r.items().size());
        assertEquals(0.5, r.items().get(0).score(), 1e-9);
    }

    @Test
    void 관련도_하한보다_낮은_후보는_버리고_모두_낮으면_결과가_비어_있다() {
        Event strong = event("관련 있는 공연", null, null);
        Event weak = event("관련 없는 공연", null, null);
        index.hits = List.of(new VectorIndex.Hit(strong.getId(), 0.42), new VectorIndex.Hit(weak.getId(), 0.29));
        when(events.findAllById(any())).thenReturn(List.of(strong, weak));

        SearchResponse r = service.search(request("아이와"));
        assertEquals(List.of("관련 있는 공연"), r.items().stream().map(i -> i.title()).toList());

        index.hits = List.of(new VectorIndex.Hit(weak.getId(), 0.29));
        assertTrue(service.search(request("아이와")).items().isEmpty(), "관련 없는 질의에는 결과를 비운다");
    }

    @Test
    void 논리삭제된_행사는_결과에서_뺀다() {
        Event archived = event("지난 공연", null, null).toBuilder().archivedAt(Instant.now()).build();
        index.hits = List.of(new VectorIndex.Hit(archived.getId(), 0.5));
        when(events.findAllById(any())).thenReturn(List.of(archived));

        assertTrue(service.search(request("아이와")).items().isEmpty());
    }

    @Test
    void 조건만_있으면_의미_검색을_하지_않고_DB_로_찾는다() {
        when(events.search(any(), eq(TODAY), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event("무료 전시", null, null))));

        SearchResponse r = service.search(request("서울 무료 전시"));

        assertEquals(0, index.searches);
        assertFalse(r.semantic());
        ArgumentCaptor<EventSearchCondition> captor = ArgumentCaptor.forClass(EventSearchCondition.class);
        verify(events).search(captor.capture(), eq(TODAY), any(Pageable.class));
        assertEquals("11", captor.getValue().sido());
        assertEquals("FREE", captor.getValue().priceType());
        assertEquals(List.of("exhibition"), captor.getValue().categories());
        assertNull(captor.getValue().keyword());
        assertEquals(1, r.items().size());
        assertNull(r.items().get(0).score());
    }

    @Test
    void 임베딩을_쓸_수_없으면_제목_글자_검색으로_대신하고_표시한다() {
        embedder.configured = false;
        when(events.search(any(), eq(TODAY), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event("어린왕자", null, null))));

        SearchResponse r = service.search(request("어린왕자"));

        assertTrue(r.degraded());
        assertEquals(0, index.searches);
        ArgumentCaptor<EventSearchCondition> captor = ArgumentCaptor.forClass(EventSearchCondition.class);
        verify(events).search(captor.capture(), eq(TODAY), any(Pageable.class));
        assertEquals("어린왕자", captor.getValue().keyword());
    }

    @Test
    void 벡터_색인이_실패해도_오류_없이_제목_검색으로_대신한다() {
        VectorIndex broken = new FakeIndex() {
            @Override public List<Hit> search(float[] vector, Filter filter, int limit) { throw new IllegalStateException("Qdrant 장애"); }
        };
        CategoryResolver categories = new CategoryResolver();
        RegionResolver regions = new RegionResolver();
        SearchProperties properties = new SearchProperties(60, 0.3, 12, 48, 100, 5, List.of(1), List.of("today"), List.of(), "Asia/Seoul", Map.of());
        SearchDictionary dictionary = new SearchDictionary(categories);
        SearchService fallback = new SearchService(new QueryAnalyzer(dictionary, regions, properties), dictionary, broken, embedder,
                events, new EventPresenter(), categories, regions, properties, Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
        when(events.search(any(), eq(TODAY), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertTrue(fallback.search(request("어린왕자")).degraded());
    }

    @Test
    void 내_주변은_반경_밖을_빼고_가까운_순으로_거리를_붙인다() {
        // 서울시청(37.5665, 126.9780) 기준: 광화문 약 0.7km, 강남역 약 8.9km
        Event near = event("광화문 공연", 37.5759, 126.9769);
        Event far = event("강남 공연", 37.4979, 127.0276);
        Event closest = event("시청 앞 공연", 37.5668, 126.9782);
        when(events.search(any(), eq(TODAY), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(far, near, closest)));

        SearchRequest r = new SearchRequest(null, null, null, null, null, null, null, null, 37.5665, 126.9780, 3, null, null);
        SearchResponse response = service.search(r);

        assertEquals(List.of("시청 앞 공연", "광화문 공연"), response.items().stream().map(i -> i.title()).toList());
        assertNotNull(response.items().get(0).distanceMeters());
        assertTrue(response.items().get(0).distanceMeters() < response.items().get(1).distanceMeters());
        assertEquals(List.of("내 주변 3km"), response.interpreted());
    }

    @Test
    void 쪽_크기는_최대를_넘지_않는다() {
        when(events.search(any(), eq(TODAY), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(new SearchRequest(null, null, null, null, null, null, null, null, null, null, null, 0, 9999));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(events).search(any(), eq(TODAY), captor.capture());
        assertEquals(48, captor.getValue().getPageSize());
        verify(events, never()).findAllById(any());
    }
}
