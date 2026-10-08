package com.t.tshow.domain.search.service;

import com.t.tshow.domain.event.dto.EventSearchCondition;
import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.event.service.EventPresenter;
import com.t.tshow.domain.index.port.Embedder;
import com.t.tshow.domain.index.port.VectorIndex;
import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.search.dto.EventCard;
import com.t.tshow.domain.search.dto.SearchQuery;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.global.config.SearchProperties;
import com.t.tshow.global.util.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 행사 검색. 조건(날짜·지역·분류·가격·반경)을 먼저 걸고, 검색 문장이 있으면 그 안에서 의미가 비슷한 순서로 찾는다.
 * 관련도가 하한(tshow.search.min-score)에 못 미치는 후보는 버린다. 벡터 검색을 쓸 수 없으면(임베딩 키 없음·Qdrant 장애)
 * 제목 글자 검색으로 대신하고 그 사실을 응답에 표시한다.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final QueryAnalyzer analyzer;
    private final SearchDictionary dictionary;
    private final VectorIndex index;
    private final Embedder embedder;
    private final EventRepository events;
    private final EventPresenter presenter;
    private final CategoryResolver categories;
    private final RegionResolver regions;
    private final SearchProperties properties;
    private final Clock clock;

    public SearchService(QueryAnalyzer analyzer, SearchDictionary dictionary, VectorIndex index, Embedder embedder,
                         EventRepository events, EventPresenter presenter, CategoryResolver categories,
                         RegionResolver regions, SearchProperties properties, Clock clock) {
        this.analyzer = analyzer;
        this.dictionary = dictionary;
        this.index = index;
        this.embedder = embedder;
        this.events = events;
        this.presenter = presenter;
        this.categories = categories;
        this.regions = regions;
        this.properties = properties;
        this.clock = clock;
    }

    public SearchResponse search(SearchRequest request) {
        LocalDate today = LocalDate.now(clock);
        SearchQuery query = analyzer.analyze(request, today);
        int size = size(request);
        int page = request.page() == null ? 0 : Math.max(0, request.page());
        List<String> interpreted = interpretation(query);

        if (query.semantic()) {
            try {
                return semantic(query, interpreted, today, page, size);
            } catch (RuntimeException e) {
                log.warn("의미 검색을 하지 못해 제목 검색으로 대신해요: {}", e.getMessage());
                return byConditions(query, interpreted, query.text(), true, today, page, size);
            }
        }
        return byConditions(query, interpreted, null, false, today, page, size);
    }

    /** 의미 검색: 조건을 Qdrant 필터로 먼저 걸고, 문장과 가까운 순서로. 관련도 하한 아래는 버린다 */
    private SearchResponse semantic(SearchQuery q, List<String> interpreted, LocalDate today, int page, int size) {
        if (!embedder.isConfigured()) throw new IllegalStateException("임베딩을 쓸 수 없어요");
        float[] vector = embedder.embed(List.of(q.text())).get(0);
        List<VectorIndex.Hit> hits = index.search(vector, filter(q), properties.candidateLimit()).stream()
                .filter(h -> h.score() >= properties.minScore()).toList();

        Map<UUID, Event> byId = events.findAllById(hits.stream().map(VectorIndex.Hit::id).toList()).stream()
                .filter(e -> e.getArchivedAt() == null)
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        List<Event> ordered = hits.stream().map(h -> byId.get(h.id())).filter(java.util.Objects::nonNull).toList();
        Map<UUID, Double> scores = hits.stream().collect(Collectors.toMap(VectorIndex.Hit::id, VectorIndex.Hit::score, (a, b) -> a));
        return respond(q, interpreted, q.text(), true, false, ordered, scores, today, page, size);
    }

    /** 조건만으로(또는 의미 검색 대신 제목 글자로) 찾는다 */
    private SearchResponse byConditions(SearchQuery q, List<String> interpreted, String keyword, boolean degraded,
                                        LocalDate today, int page, int size) {
        EventSearchCondition condition = new EventSearchCondition(q.kinds(), q.categories(), q.sido(), q.sigungu(),
                q.free() ? "FREE" : null, q.from(), q.to(), keyword,
                q.near() == null ? null : new EventSearchCondition.Near(q.near().lat(), q.near().lon(), q.near().radiusMeters()));
        // 내 주변은 정확한 거리를 계산해 가까운 순으로 정렬해야 하므로 후보를 모아 메모리에서 쪽을 나눈다
        if (q.near() != null) {
            List<Event> found = events.search(condition, today, PageRequest.of(0, properties.candidateLimit() * 4)).getContent();
            List<Event> ordered = found.stream().filter(e -> distance(q, e) != null && distance(q, e) <= q.near().radiusMeters())
                    .sorted(Comparator.comparingDouble(e -> distance(q, e))).toList();
            return respond(q, interpreted, keyword == null ? "" : keyword, false, degraded, ordered, Map.of(), today, page, size);
        }
        var found = events.search(condition, today, PageRequest.of(page, size));
        return new SearchResponse(keyword == null ? "" : keyword, interpreted, false, degraded,
                found.getContent().stream().map(e -> card(e, q, null, today)).toList(), page, size, found.getTotalElements(),
                found.getTotalPages());
    }

    /** 후보 전체를 쪽으로 나눠 응답한다 */
    private SearchResponse respond(SearchQuery q, List<String> interpreted, String text, boolean semantic, boolean degraded,
                                   List<Event> ordered, Map<UUID, Double> scores, LocalDate today, int page, int size) {
        int from = Math.min(page * size, ordered.size());
        List<EventCard> items = ordered.subList(from, Math.min(from + size, ordered.size())).stream()
                .map(e -> card(e, q, scores.get(e.getId()), today)).toList();
        int totalPages = (int) Math.ceil(ordered.size() / (double) size);
        return new SearchResponse(text, interpreted, semantic, degraded, items, page, size, ordered.size(), totalPages);
    }

    private VectorIndex.Filter filter(SearchQuery q) {
        return new VectorIndex.Filter(q.from().toEpochDay(), q.to() == null ? null : q.to().toEpochDay(),
                q.kinds(), q.categories(), q.sido(), q.sigungu(), q.free() ? "FREE" : null,
                q.near() == null ? null : new VectorIndex.Geo(q.near().lat(), q.near().lon(), q.near().radiusMeters()));
    }

    private EventCard card(Event e, SearchQuery q, Double score, LocalDate today) {
        EventPresenter.Schedule schedule = presenter.schedule(e.getStartDate(), e.getEndDate(), today);
        Double meters = distance(q, e);
        return new EventCard(e.getId(), e.getTitle(), categories.kindName(e.getKind()), categories.name(e.getCategory()),
                e.getImageUrl(), e.getStartDate(), e.getEndDate(), schedule.period(), schedule.status(), schedule.tone(),
                e.getVenueName(), regions.shortName(e.getSidoCode(), e.getSigunguCode()), e.getPriceType(),
                meters == null ? null : (int) Math.round(meters), score);
    }

    /** 내 주변 검색일 때 내 위치에서 행사까지의 거리(m). 아니거나 행사 좌표를 모르면 null */
    private static Double distance(SearchQuery q, Event e) {
        if (q.near() == null || e.getLat() == null || e.getLon() == null) return null;
        return Geo.distanceMeters(q.near().lat(), q.near().lon(), e.getLat(), e.getLon());
    }

    private int size(SearchRequest request) {
        int size = request.size() == null ? properties.pageSize() : request.size();
        return Math.max(1, Math.min(size, properties.maxPageSize()));
    }

    /** "이렇게 이해했어요" 에 보일 조건 이름들 */
    private List<String> interpretation(SearchQuery q) {
        List<String> names = new ArrayList<>();
        if (q.sido() != null) names.add(regions.shortName(q.sido(), q.sigungu()));
        if (q.dateKey() != null) names.add(dateLabel(q.dateKey()));
        else if (q.to() != null) names.add(q.from().equals(q.to()) ? q.from().toString() : q.from() + " ~ " + q.to());
        if (q.free()) names.add("무료");
        q.kinds().forEach(k -> names.add(categories.kindName(k)));
        q.categories().forEach(c -> names.add(categories.name(c)));
        if (q.near() != null) names.add("내 주변 " + Math.round(q.near().radiusMeters() / 1000) + "km");
        return names;
    }

    /** 날짜 규칙을 사전의 첫 번째 표현(이번 주말…)으로 */
    private String dateLabel(String key) {
        DateRule rule = DateRule.fromKey(key);
        return dictionary.dateLabels().getOrDefault(rule, key);
    }
}
