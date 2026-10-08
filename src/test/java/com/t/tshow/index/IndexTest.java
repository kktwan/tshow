package com.t.tshow.index;

import com.t.tshow.event.Event;
import com.t.tshow.global.config.IndexProperties;
import com.t.tshow.ingest.normalize.CategoryResolver;
import com.t.tshow.ingest.normalize.RegionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IndexTest {

    private final IndexProperties properties = new IndexProperties("test-events", 4, 2, 50, "index/embedding.tpl", true);
    private final CategoryResolver categories = new CategoryResolver();
    private final RegionResolver regions = new RegionResolver();
    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder(properties, categories, regions);
    private final PayloadBuilder payloadBuilder = new PayloadBuilder();

    // ───────────── 가짜 외부 연동 ─────────────

    /** 메모리 벡터 색인. 호출 횟수를 센다 */
    private static class FakeIndex implements VectorIndex {
        final Map<UUID, Point> points = new LinkedHashMap<>();
        int upserts, payloadOverwrites, deletes;
        boolean failNextUpsert;

        @Override public void ensureCollection() { }
        @Override public void upsert(List<Point> list) {
            if (failNextUpsert) { failNextUpsert = false; throw new IllegalStateException("Qdrant 장애"); }
            upserts += list.size();
            list.forEach(p -> points.put(p.id(), p));
        }
        @Override public void overwritePayload(UUID id, Map<String, Object> payload) {
            payloadOverwrites++;
            Point old = points.get(id);
            points.put(id, new Point(id, old.vector(), payload));
        }
        @Override public void delete(Collection<UUID> ids) { deletes += ids.size(); ids.forEach(points::remove); }
        @Override public Set<UUID> allIds() { return new HashSet<>(points.keySet()); }
        @Override public List<Hit> search(float[] vector, Filter filter, int limit) { return List.of(); }
    }

    private static class FakeEmbedder implements Embedder {
        boolean configured = true;
        int embedded;

        @Override public boolean isConfigured() { return configured; }
        @Override public List<float[]> embed(List<String> texts) {
            embedded += texts.size();
            return texts.stream().map(t -> new float[]{t.length(), t.hashCode() % 7, 1, 0}).toList();
        }
    }

    /** 메모리 상태 저장소: 활성 행사 목록과 색인 상태 */
    private static class FakeStore implements IndexStateStore {
        final Map<UUID, Event> events = new LinkedHashMap<>();
        final Set<UUID> archived = new HashSet<>();
        final Map<UUID, String[]> state = new HashMap<>();

        @Override public List<Stored> activeEvents() {
            List<Stored> list = new ArrayList<>();
            events.forEach((id, e) -> {
                if (archived.contains(id)) return;
                String[] s = state.get(id);
                list.add(new Stored(e, s == null ? null : s[0], s == null ? null : s[1], s != null));
            });
            return list;
        }
        @Override public List<UUID> archivedStillIndexed() {
            return archived.stream().filter(state::containsKey).toList();
        }
        @Override public void markIndexed(UUID id, String embedHash, String payloadHash) { state.put(id, new String[]{embedHash, payloadHash}); }
        @Override public void clearIndexed(Collection<UUID> ids) { ids.forEach(state::remove); }
    }

    private FakeIndex index;
    private FakeEmbedder embedder;
    private FakeStore store;
    private IndexService service;

    @BeforeEach
    void setUp() {
        index = new FakeIndex();
        embedder = new FakeEmbedder();
        store = new FakeStore();
        service = new IndexService(store, index, embedder, textBuilder, payloadBuilder, properties);
    }

    private static Event event(String title, String description, String priceType, LocalDate start, LocalDate end) {
        return new Event(UUID.randomUUID(), "PERFORMANCE", "theater", title, title, description, start, end,
                "시흥아트센터", "시흥아트센터", "경기도 시흥시", "41", null, 37.37, 126.72, priceType, null,
                null, null, null, "박문경", "시흥시청", null, null, null, null, 1, description != null, "hash");
    }

    private Event add(Event e) {
        store.events.put(e.id(), e);
        return e;
    }

    private static final LocalDate D1 = LocalDate.of(2026, 10, 28);

    // ───────────── 임베딩 텍스트 ─────────────

    @Test
    void 임베딩_텍스트는_값이_없는_줄을_빼고_날짜와_가격은_넣지_않는다() {
        Event e = event("패밀리 음악회", null, "PAID", D1, D1);
        String text = textBuilder.build(e);
        assertTrue(text.contains("제목: 패밀리 음악회"));
        assertTrue(text.contains("분류: 연극"), "분류는 표준 분류의 이름");
        assertTrue(text.contains("장소: 시흥아트센터"));
        assertTrue(text.contains("출연: 박문경"));
        assertFalse(text.contains("소개:"), "설명이 없으면 그 줄은 빠진다");
        assertFalse(text.contains("2026"), "날짜는 임베딩 텍스트에 넣지 않는다");
        assertFalse(text.contains("PAID"));
    }

    @Test
    void 긴_설명은_앞부분만_넣고_공백을_정리한다() {
        String longText = "가나다  라마바\n" + "사".repeat(200);
        String text = textBuilder.build(event("제목", longText, "FREE", D1, D1));
        String intro = text.substring(text.indexOf("소개: ") + 4);
        assertEquals(50, intro.length(), "설정한 최대 글자 수(50)로 자른다");
        assertTrue(intro.startsWith("가나다 라마바 "), "연속 공백·줄바꿈은 한 칸으로");
    }

    @Test
    void 지역은_사람이_읽는_이름으로_넣는다() {
        Event e = new Event(UUID.randomUUID(), "PERFORMANCE", "theater", "제목", "제목", null, D1, D1, null, null, null,
                "11", "710", null, null, "FREE", null, null, null, null, null, null, null, null, null, null, 1, false, "h");
        assertTrue(textBuilder.build(e).contains("지역: 서울특별시 송파구"));
    }

    // ───────────── payload ─────────────

    @Test
    void payload_는_날짜를_일수로_좌표를_객체로_담고_없는_값은_넣지_않는다() {
        Map<String, Object> p = payloadBuilder.build(event("제목", "설명", "FREE", D1, D1.plusDays(3)));
        assertEquals(D1.toEpochDay(), p.get("start_day"));
        assertEquals(D1.plusDays(3).toEpochDay(), p.get("end_day"));
        assertEquals("FREE", p.get("price_type"));
        assertEquals(Map.of("lat", 37.37, "lon", 126.72), p.get("location"));
        assertFalse(p.containsKey("sigungu"), "값이 없으면 필드를 넣지 않는다");

        Event noEnd = event("제목", null, "UNKNOWN", D1, null);
        assertEquals(D1.toEpochDay(), payloadBuilder.build(noEnd).get("end_day"), "종료일이 없으면 시작일과 같다고 본다");
    }

    @Test
    void 해시는_같은_내용이면_같고_키_순서와_무관하다() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("x", 1L);
        a.put("y", "z");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("y", "z");
        b.put("x", 1L);
        assertEquals(Hashes.ofPayload(a), Hashes.ofPayload(b));
        b.put("x", 2L);
        assertNotEquals(Hashes.ofPayload(a), Hashes.ofPayload(b));
    }

    // ───────────── 동기화 ─────────────

    @Test
    void 처음에는_모든_행사를_임베딩해서_올리고_상태를_기록한다() {
        add(event("가", null, "FREE", D1, D1));
        add(event("나", "설명", "PAID", D1, D1));
        add(event("다", null, "FREE", D1, D1));

        IndexService.Summary s = service.sync();

        assertEquals(3, s.embedded());
        assertEquals(3, index.points.size());
        assertEquals(3, store.state.size());
        assertEquals(0, s.failed());
    }

    @Test
    void 다시_실행하면_바뀐_게_없어_임베딩하지_않는다() {
        add(event("가", null, "FREE", D1, D1));
        add(event("나", null, "FREE", D1, D1));
        service.sync();
        int embeddedBefore = embedder.embedded;

        IndexService.Summary again = service.sync();

        assertEquals(0, again.embedded());
        assertEquals(2, again.unchanged());
        assertEquals(embeddedBefore, embedder.embedded, "임베딩 호출이 늘면 안 된다 (비용)");
    }

    @Test
    void 날짜_가격만_바뀌면_재임베딩_없이_payload_만_갱신한다() {
        Event e = add(event("가", "설명", "UNKNOWN", D1, D1));
        service.sync();
        int embeddedBefore = embedder.embedded;

        store.events.put(e.id(), event("가", "설명", "FREE", D1.plusDays(7), D1.plusDays(7)).withId(e.id()));
        IndexService.Summary s = service.sync();

        assertEquals(0, s.embedded());
        assertEquals(1, s.payloadUpdated());
        assertEquals(embeddedBefore, embedder.embedded);
        assertEquals("FREE", index.points.get(e.id()).payload().get("price_type"));
        assertEquals(D1.plusDays(7).toEpochDay(), index.points.get(e.id()).payload().get("start_day"));
    }

    @Test
    void 제목이나_설명이_바뀌면_다시_임베딩한다() {
        Event e = add(event("가", "설명", "FREE", D1, D1));
        service.sync();

        store.events.put(e.id(), event("가 앙코르", "설명", "FREE", D1, D1).withId(e.id()));
        IndexService.Summary s = service.sync();

        assertEquals(1, s.embedded());
        assertEquals(0, s.payloadUpdated());
    }

    @Test
    void 논리삭제된_행사와_DB에_없는_점은_색인에서_지운다() {
        Event keep = add(event("유지", null, "FREE", D1, D1));
        Event old = add(event("종료", null, "FREE", D1, D1));
        service.sync();
        assertEquals(2, index.points.size());

        store.archived.add(old.id());                 // 보관 기간이 지나 논리삭제
        UUID orphan = UUID.randomUUID();              // DB 에서는 사라졌지만 색인에 남은 점
        index.points.put(orphan, new VectorIndex.Point(orphan, new float[]{1, 2, 3, 4}, Map.of()));

        IndexService.Summary s = service.sync();

        assertEquals(2, s.deleted());
        assertEquals(Set.of(keep.id()), index.points.keySet());
        assertFalse(store.state.containsKey(old.id()), "지운 행사의 색인 상태도 지운다");
    }

    @Test
    void 임베딩_키가_없으면_아무것도_하지_않는다() {
        add(event("가", null, "FREE", D1, D1));
        embedder.configured = false;

        IndexService.Summary s = service.sync();

        assertTrue(s.skipped());
        assertEquals(0, index.points.size());
        assertEquals(0, embedder.embedded);
    }

    @Test
    void 한_묶음이_실패해도_나머지는_계속하고_실패한_행사는_다음에_다시_시도한다() {
        // 묶음 크기 2: 3건이면 [2건, 1건]
        add(event("가", null, "FREE", D1, D1));
        add(event("나", null, "FREE", D1, D1));
        add(event("다", null, "FREE", D1, D1));
        index.failNextUpsert = true;

        IndexService.Summary first = service.sync();
        assertEquals(2, first.failed());
        assertEquals(1, first.embedded());
        assertEquals(1, store.state.size(), "실패한 묶음은 상태를 기록하지 않는다");

        IndexService.Summary retry = service.sync();
        assertEquals(2, retry.embedded(), "실패했던 행사만 다시 올린다");
        assertEquals(3, index.points.size());
    }
}
