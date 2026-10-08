package com.t.tshow.domain.recommend.service;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.event.service.EventPresenter;
import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.recommend.dto.AiPick;
import com.t.tshow.domain.recommend.dto.AiRecommendResponse;
import com.t.tshow.domain.recommend.port.AiCurator;
import com.t.tshow.domain.search.dto.EventCard;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.domain.search.service.SearchService;
import com.t.tshow.global.config.RecommendProperties;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import com.t.tshow.global.util.Texts;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 추천. 일반 검색과 같은 조건·순서로 후보를 뽑고(검색 서비스 재사용), AI 가 그 안에서 고르고 이유를 붙인다.
 * 버튼을 눌렀을 때만 호출되며, 비용을 막으려고 사람별·전체 하루 한도와 같은 조건의 결과 캐시를 둔다.
 * AI 가 시간 초과·실패하면 오류 대신 검색 순서 그대로 보여 주고 그 사실을 알린다. AI 가 후보 밖의 번호를 말하면 무시한다.
 */
@Service
public class RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN);
    private static final int MAX_REASON_CHARS = 90;

    private final SearchService search;
    private final EventRepository events;
    private final AiCurator curator;
    private final EventPresenter presenter;
    private final CategoryResolver categories;
    private final RegionResolver regions;
    private final RecommendProperties properties;
    private final Clock clock;
    private final UsageLimiter limiter;
    private final ResultCache<AiRecommendResponse> cache;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public RecommendService(SearchService search, EventRepository events, AiCurator curator, EventPresenter presenter,
                            CategoryResolver categories, RegionResolver regions, RecommendProperties properties, Clock clock) {
        this.search = search;
        this.events = events;
        this.curator = curator;
        this.presenter = presenter;
        this.categories = categories;
        this.regions = regions;
        this.properties = properties;
        this.clock = clock;
        this.limiter = new UsageLimiter(properties.dailyLimitPerClient(), properties.dailyLimitTotal(), clock);
        this.cache = new ResultCache<>(properties.cacheSize(), Duration.ofMinutes(properties.cacheMinutes()), clock);
    }

    @PreDestroy
    void close() {
        pool.shutdownNow();
    }

    /** AI 를 쓸 수 있는지 (화면에서 버튼을 보일지 정한다) */
    public boolean available() {
        return curator.isConfigured();
    }

    /**
     * @param client 사용 횟수를 셀 사람 (IP)
     * @throws BusinessException 하루 한도를 넘었을 때 (RATE_LIMITED)
     */
    public AiRecommendResponse recommend(SearchRequest request, String client) {
        if (!available()) {
            return AiRecommendResponse.notice("AI 추천은 지금 쓸 수 없어요. 아래 검색 결과를 확인해 주세요.", List.of());
        }
        String key = cacheKey(request);
        AiRecommendResponse cached = key == null ? null : cache.get(key);
        if (cached != null) return cached;

        SearchResponse found = search.search(new SearchRequest(request.q(), request.kind(), request.category(), request.sido(),
                request.sigungu(), request.when(), request.date(), request.free(), request.lat(), request.lon(),
                request.radiusKm(), 0, properties.candidates()));
        if (found.items().isEmpty()) {
            return AiRecommendResponse.notice("조건에 맞는 행사가 없어서 AI 추천을 하지 않았어요. 조건을 줄여 보세요.", found.interpreted());
        }
        if (!limiter.tryConsume(client)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "오늘 AI 추천을 모두 사용했어요. 내일 다시 이용해 주세요.");
        }

        Map<UUID, Event> byId = events.findAllById(found.items().stream().map(EventCard::id).toList()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        List<EventCard> cards = found.items().stream().filter(c -> byId.containsKey(c.id())).toList();
        LocalDate today = LocalDate.now(clock);
        List<AiCurator.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            candidates.add(new AiCurator.Candidate(i + 1, line(byId.get(cards.get(i).id()), today)));
        }

        AiRecommendResponse response;
        try {
            Future<AiCurator.Curation> future = pool.submit(() -> curator.curate(requestText(request, found), TODAY.format(today), candidates));
            try {
                response = toResponse(future.get(properties.timeoutSeconds(), TimeUnit.SECONDS), cards, found);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("AI 추천 시간 초과({}초)", properties.timeoutSeconds());
                response = fallback(cards, found, "AI 응답이 늦어져서 검색 순서대로 보여 드려요.");
            }
        } catch (ExecutionException e) {
            log.warn("AI 추천 실패: {}", causes(e));
            response = fallback(cards, found, "AI 설명을 만들지 못해서 검색 순서대로 보여 드려요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response = fallback(cards, found, "AI 설명을 만들지 못해서 검색 순서대로 보여 드려요.");
        }
        if (response.ai() && key != null) cache.put(key, response);
        return response;
    }

    /** AI 의 답을 화면용 결과로. 후보 밖의 번호와 중복은 버린다 */
    private AiRecommendResponse toResponse(AiCurator.Curation curation, List<EventCard> cards, SearchResponse found) {
        List<AiPick> picks = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (AiCurator.Pick pick : curation.picks()) {
            if (picks.size() >= properties.maxPicks()) break;
            if (pick.no() < 1 || pick.no() > cards.size() || !used.add(pick.no())) continue;
            picks.add(new AiPick(cards.get(pick.no() - 1), clip(pick.reason(), MAX_REASON_CHARS)));
        }
        if (picks.isEmpty() && !curation.picks().isEmpty()) {
            return fallback(cards, found, "AI 설명을 만들지 못해서 검색 순서대로 보여 드려요.");
        }
        if (picks.isEmpty()) {
            // AI 가 정상 응답했지만 맞는 행사가 없다고 했다. 관련 없는 후보를 그대로 보여 주지 않고 솔직히 알린다
            return new AiRecommendResponse(null, List.of(), true, "요청에 딱 맞는 행사를 찾지 못했어요. 조건을 바꿔 보세요.", found.interpreted());
        }
        return new AiRecommendResponse(Texts.blankToNull(curation.summary()), picks, true, null, found.interpreted());
    }

    private AiRecommendResponse fallback(List<EventCard> cards, SearchResponse found, String message) {
        List<AiPick> picks = cards.stream().limit(properties.maxPicks()).map(c -> new AiPick(c, null)).toList();
        return new AiRecommendResponse(null, picks, false, message, found.interpreted());
    }

    /** AI 에게 주는 요청 글: 사용자가 쓴 문장과, 이미 후보에 적용된 조건 */
    private static String requestText(SearchRequest request, SearchResponse found) {
        String q = Texts.blankToNull(request.q());
        StringBuilder sb = new StringBuilder(q == null ? "(검색어 없음)" : q);
        if (!found.interpreted().isEmpty()) sb.append("\n적용된 조건: ").append(String.join(", ", found.interpreted()));
        return sb.toString();
    }

    /** 후보 한 건을 한 줄로. AI 응답 시간이 입력·출력 토큰에 비례하므로 필요한 것만 짧게 담는다 */
    private String line(Event e, LocalDate today) {
        EventPresenter.Schedule schedule = presenter.schedule(e.getStartDate(), e.getEndDate(), today);
        List<String> parts = new ArrayList<>();
        parts.add(clip(e.getTitle(), 60));
        parts.add(categories.name(e.getCategory()));
        parts.add("기간 " + schedule.period() + (schedule.status() == null ? "" : " · " + schedule.status()));
        String place = String.join(" ", List.of(Texts.nullToEmpty(e.getVenueName()), regions.shortName(e.getSidoCode(), e.getSigunguCode()))).trim();
        if (!place.isEmpty()) parts.add("장소 " + clip(place, 50));
        if ("FREE".equals(e.getPriceType())) parts.add("무료");
        else if (e.getPriceText() != null) parts.add("가격 " + clip(Texts.collapseSpaces(e.getPriceText()), 40));
        if (e.getAgeText() != null) parts.add("연령 " + clip(Texts.collapseSpaces(e.getAgeText()), 30));
        if (e.getCastText() != null) parts.add("출연 " + clip(Texts.collapseSpaces(e.getCastText()), 40));
        if (e.getDescription() != null) parts.add("소개 " + clip(Texts.collapseSpaces(e.getDescription()), properties.descriptionChars()));
        return String.join(" | ", parts);
    }

    /** 같은 조건이면 같은 키. 내 주변은 위치마다 결과(거리)가 달라 캐시하지 않는다 */
    private String cacheKey(SearchRequest r) {
        if (r.lat() != null && r.lon() != null) return null;
        return String.join("|", LocalDate.now(clock).toString(), Texts.collapseSpaces(r.q()), Texts.nullToEmpty(r.kind()),
                Texts.nullToEmpty(r.category()), Texts.nullToEmpty(r.sido()), Texts.nullToEmpty(r.sigungu()),
                Texts.nullToEmpty(r.when()), String.valueOf(r.date()), String.valueOf(Boolean.TRUE.equals(r.free())));
    }

    /** 원인 사슬을 한 줄로 (래핑된 예외에는 진짜 원인이 안쪽에 있다) */
    private static String causes(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 600; t = t.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return sb.toString();
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max).trim() + "…";
    }
}
