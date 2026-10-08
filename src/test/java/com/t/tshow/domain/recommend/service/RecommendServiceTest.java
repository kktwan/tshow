package com.t.tshow.domain.recommend.service;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.event.repository.EventRepository;
import com.t.tshow.domain.event.service.EventPresenter;
import com.t.tshow.domain.ingest.service.normalize.CategoryResolver;
import com.t.tshow.domain.ingest.service.normalize.RegionResolver;
import com.t.tshow.domain.recommend.dto.AiRecommendResponse;
import com.t.tshow.domain.recommend.port.AiCurator;
import com.t.tshow.domain.search.dto.EventCard;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.domain.search.service.SearchService;
import com.t.tshow.global.config.RecommendProperties;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 외부(검색·DB·Gemini)는 가짜로 두고 후보 번호 처리, 캐시·한도, 실패 시 대체 동작을 확인한다 */
class RecommendServiceTest {

    /** 2026-10-08 목요일 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static class FakeCurator implements AiCurator {
        boolean configured = true;
        int calls;
        List<Candidate> lastCandidates;
        String lastRequest;
        Supplier<Curation> answer = () -> new Curation("요약", List.of());

        @Override public boolean isConfigured() { return configured; }
        @Override public Curation curate(String request, String today, List<Candidate> candidates) {
            calls++;
            lastRequest = request;
            lastCandidates = candidates;
            return answer.get();
        }
    }

    private final FakeCurator curator = new FakeCurator();
    private final SearchService search = Mockito.mock(SearchService.class);
    private final EventRepository events = Mockito.mock(EventRepository.class);
    private List<Event> stored;
    private List<EventCard> cards;

    private RecommendService service(int perClient, int timeoutSeconds) {
        RecommendProperties props = new RecommendProperties(20, 2, timeoutSeconds, perClient, 100, 30, 50, 60);
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        return new RecommendService(search, events, curator, new EventPresenter(), new CategoryResolver(), new RegionResolver(),
                props, clock);
    }

    @BeforeEach
    void setUp() {
        stored = new ArrayList<>();
        cards = new ArrayList<>();
        for (String title : List.of("어린이 뮤지컬", "가족 연극", "동화 인형극")) {
            Event e = Event.builder().id(UUID.randomUUID()).kind("PERFORMANCE").category("theater").title(title).titleNorm(title)
                    .description("아이와 함께 보는 공연 " + title).startDate(TODAY).endDate(TODAY.plusDays(5)).venueName("시흥아트센터")
                    .sidoCode("41").ageText("만 5세 이상").priceType("FREE").sourceCount(1).dataHash("h")
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
            stored.add(e);
            cards.add(new EventCard(e.getId(), title, "공연", "연극", null, TODAY, TODAY.plusDays(5), "10.8 (목) – 10.13 (화)", "진행 중",
                    "live", "시흥아트센터", "경기 시흥시", "FREE", null, 0.5, false));
        }
        when(search.search(any())).thenAnswer(inv -> new SearchResponse("", List.of("경기"), true, false, cards, 0, 20, cards.size(), 1));
        when(events.findAllById(any())).thenAnswer(inv -> stored);
    }

    private static SearchRequest request(String q) {
        return new SearchRequest(q, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void AI_가_고른_번호를_행사로_바꾸고_후보_밖_번호와_중복은_버린다() {
        curator.answer = () -> new AiCurator.Curation("아이와 보기 좋아요", List.of(
                new AiCurator.Pick(2, "만 5세 이상이라 아이와 보기 좋아요."),
                new AiCurator.Pick(9, "없는 후보"),
                new AiCurator.Pick(2, "중복"),
                new AiCurator.Pick(1, "무료 공연이에요."),
                new AiCurator.Pick(3, "최대 개수(2)를 넘으면 버린다")));

        AiRecommendResponse r = service(10, 5).recommend(request("아이와 갈 만한 공연"), "ip1");

        assertTrue(r.ai());
        assertEquals("아이와 보기 좋아요", r.summary());
        assertEquals(List.of("가족 연극", "어린이 뮤지컬"), r.picks().stream().map(p -> p.card().title()).toList());
        assertEquals("만 5세 이상이라 아이와 보기 좋아요.", r.picks().get(0).reason());
        assertEquals(List.of("경기"), r.interpreted());
    }

    @Test
    void AI_에게는_번호와_짧은_한_줄_설명만_보낸다() {
        curator.answer = () -> new AiCurator.Curation("", List.of(new AiCurator.Pick(1, "x")));

        service(10, 5).recommend(request("아이와 갈 만한 공연"), "ip1");

        assertEquals(3, curator.lastCandidates.size());
        assertEquals(1, curator.lastCandidates.get(0).no());
        String line = curator.lastCandidates.get(0).line();
        assertTrue(line.contains("어린이 뮤지컬") && line.contains("무료") && line.contains("연령 만 5세 이상"), line);
        assertTrue(curator.lastRequest.contains("아이와 갈 만한 공연") && curator.lastRequest.contains("적용된 조건: 경기"));
    }

    @Test
    void 같은_조건은_캐시로_답하고_횟수를_쓰지_않는다() {
        curator.answer = () -> new AiCurator.Curation("요약", List.of(new AiCurator.Pick(1, "이유")));
        RecommendService service = service(1, 5);

        AiRecommendResponse first = service.recommend(request("아이와 갈 만한 공연"), "ip1");
        AiRecommendResponse second = service.recommend(request(" 아이와  갈 만한 공연 "), "ip1");

        assertEquals(1, curator.calls, "같은 조건이면 AI 를 다시 부르지 않는다");
        assertEquals(first, second);
        // 하루 한도(1회)를 이미 썼어도 캐시 응답은 나온다
        assertDoesNotThrow(() -> service.recommend(request("아이와 갈 만한 공연"), "ip1"));
    }

    @Test
    void 내_주변_추천은_위치마다_달라서_캐시하지_않는다() {
        curator.answer = () -> new AiCurator.Curation("요약", List.of(new AiCurator.Pick(1, "이유")));
        RecommendService service = service(10, 5);
        SearchRequest near = new SearchRequest("공연", null, null, null, null, null, null, null, 37.5, 127.0, 3, null, null);

        service.recommend(near, "ip1");
        service.recommend(near, "ip1");

        assertEquals(2, curator.calls);
    }

    @Test
    void 하루_한도를_넘으면_거절한다() {
        curator.answer = () -> new AiCurator.Curation("요약", List.of(new AiCurator.Pick(1, "이유")));
        RecommendService service = service(1, 5);
        service.recommend(request("공연"), "ip1");

        BusinessException e = assertThrows(BusinessException.class, () -> service.recommend(request("전시"), "ip1"));
        assertEquals(ErrorCode.RATE_LIMITED, e.errorCode());
        assertDoesNotThrow(() -> service.recommend(request("전시"), "ip2"), "다른 사람은 따로 센다");
    }

    @Test
    void AI_를_쓸_수_없으면_검색도_하지_않고_안내만_한다() {
        curator.configured = false;

        AiRecommendResponse r = service(10, 5).recommend(request("공연"), "ip1");

        assertFalse(r.ai());
        assertTrue(r.picks().isEmpty());
        assertNotNull(r.message());
        verify(search, never()).search(any());
        assertEquals(0, curator.calls);
    }

    @Test
    void 후보가_없으면_AI_를_부르지_않고_횟수도_쓰지_않는다() {
        when(search.search(any())).thenReturn(new SearchResponse("", List.of(), true, false, List.of(), 0, 20, 0, 0));
        RecommendService service = service(1, 5);

        AiRecommendResponse r = service.recommend(request("없는 행사"), "ip1");

        assertFalse(r.ai());
        assertEquals(0, curator.calls);
        // 횟수를 쓰지 않았으므로 이어서 정상 요청이 된다
        when(search.search(any())).thenAnswer(inv -> new SearchResponse("", List.of(), true, false, cards, 0, 20, cards.size(), 1));
        curator.answer = () -> new AiCurator.Curation("요약", List.of(new AiCurator.Pick(1, "이유")));
        assertTrue(service.recommend(request("공연"), "ip1").ai());
    }

    @Test
    void AI_가_실패하면_검색_순서대로_보여_주고_알린다() {
        curator.answer = () -> { throw new IllegalStateException("Gemini 장애"); };

        AiRecommendResponse r = service(10, 5).recommend(request("공연"), "ip1");

        assertFalse(r.ai());
        assertEquals(2, r.picks().size(), "최대 개수만큼 위에서부터");
        assertEquals("어린이 뮤지컬", r.picks().get(0).card().title());
        assertNull(r.picks().get(0).reason());
        assertNotNull(r.message());
    }

    @Test
    void AI_가_후보에_없는_번호만_말하면_검색_순서대로_보여_준다() {
        curator.answer = () -> new AiCurator.Curation("요약", List.of(new AiCurator.Pick(99, "엉뚱한 번호")));

        AiRecommendResponse r = service(10, 5).recommend(request("공연"), "ip1");

        assertFalse(r.ai());
        assertEquals(2, r.picks().size());
    }

    @Test
    void AI_가_맞는_행사가_없다고_하면_관련_없는_후보를_보여_주지_않는다() {
        curator.answer = () -> new AiCurator.Curation("", List.of());

        AiRecommendResponse r = service(10, 5).recommend(request("공연"), "ip1");

        assertTrue(r.ai());
        assertTrue(r.picks().isEmpty());
        assertNotNull(r.message());
    }

    @Test
    void AI_응답이_너무_늦으면_기다리지_않고_검색_순서대로_보여_준다() {
        curator.answer = () -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new AiCurator.Curation("늦은 요약", List.of(new AiCurator.Pick(1, "이유")));
        };

        AiRecommendResponse r = service(10, 1).recommend(request("공연"), "ip1");

        assertFalse(r.ai());
        assertEquals(2, r.picks().size());
        assertTrue(r.message().contains("늦어"));
    }
}
