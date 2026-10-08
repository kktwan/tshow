package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.event.entity.Event;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import com.t.tshow.domain.ingest.service.normalize.TextNormalizer;
import com.t.tshow.global.config.MergeProperties;
import com.t.tshow.support.TestProperties;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MergeTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 28);

    private final TextNormalizer texts = new TextNormalizer(TestProperties.normalize());
    private final MergeProperties props = new MergeProperties(0.85, 0.6, 300, 0, 8, 0.95, 1.0,
            Map.of("default", List.of("KOPIS", "CULTURE", "TOURAPI"), "description", List.of("TOURAPI", "KOPIS", "CULTURE"),
                    "image", List.of("KOPIS", "TOURAPI", "CULTURE")), 30);
    private final DuplicateMatcher matcher = new DuplicateMatcher(props);
    private final MergePlanner planner = new MergePlanner(new Clusterer(matcher, props), new EventMerger(props));

    private final AtomicInteger ids = new AtomicInteger();

    /** 테스트용 레코드. 필요한 값만 채운다 */
    private SourceRecord rec(String source, String sourceId, String title, LocalDate start, LocalDate end, String venue,
                             Double lat, Double lon) {
        return record(source, sourceId, title, start, end, venue, lat, lon).build();
    }

    private SourceRecord.SourceRecordBuilder record(String source, String sourceId, String title, LocalDate start, LocalDate end,
                                                    String venue, Double lat, Double lon) {
        return SourceRecord.builder().id((long) ids.incrementAndGet()).source(SourceType.valueOf(source)).sourceId(sourceId)
                .kind("PERFORMANCE").category("classical").title(title).titleNorm(texts.title(title))
                .startDate(start).endDate(end).venueName(venue).venueNameNorm(texts.venue(venue)).sidoCode("11")
                .lat(lat).lon(lon).priceType("UNKNOWN").fetchedAt(Instant.now());
    }

    private SourceRecord recIn(String source, String sourceId, String title, LocalDate day, String venue, Double lat, Double lon,
                               String sido, String sigungu) {
        return record(source, sourceId, title, day, day, venue, lat, lon).sidoCode(sido).sigunguCode(sigungu).build();
    }

    private SourceRecord with(SourceRecord r, String description, String priceText, String priceType, String imageUrl) {
        return r.toBuilder().description(description).priceText(priceText).priceType(priceType).imageUrl(imageUrl).build();
    }

    // ───────────── 유사도 ─────────────

    @Test
    void 글자_2그램_유사도() {
        assertEquals(1.0, Similarity.dice("어린왕자", "어린왕자"));
        assertEquals(0.0, Similarity.dice("", "어린왕자"));
        assertTrue(Similarity.dice("모래로그린음악동화어린왕자", "모래로그린음악동화어린왕자오산") > 0.9);
        assertTrue(Similarity.dice("어린왕자", "신데렐라") < 0.3);
    }

    @Test
    void 좌표_거리() {
        // 서울시청 ~ 광화문 약 0.7km
        double d = Similarity.distanceMeters(37.5663, 126.9779, 37.5759, 126.9769);
        assertTrue(d > 900 && d < 1300, "거리: " + d);
        assertEquals(0.0, Similarity.distanceMeters(37.5, 127.0, 37.5, 127.0), 1e-6);
    }

    // ───────────── 중복 판정 ─────────────

    @Test
    void 표기만_다른_같은_공연을_같은_행사로_본다() {
        // 실제로 문화정보원과 KOPIS 에서 제목의 [지역] 꼬리표 위치만 달랐던 경우
        SourceRecord culture = rec("CULTURE", "c1", "[오산] 모래로 그린 음악동화: 어린왕자 ", D1, D1, "피아올라홀(피아올라스튜디오)", null, null);
        SourceRecord kopis = rec("KOPIS", "k1", "모래로 그린 음악동화: 어린왕자 [오산]", D1, D1, "피아올라홀(피아올라스튜디오)", null, null);
        assertTrue(matcher.isDuplicate(culture, kopis));
    }

    @Test
    void 장소_이름이_달라도_좌표가_가까우면_같은_장소로_본다() {
        SourceRecord a = rec("CULTURE", "c1", "제5회 전주미니재즈페스티벌", D1, D1, "더바인홀", 35.8200, 127.1500);
        SourceRecord b = rec("KOPIS", "k1", "제5회 전주미니재즈페스티벌", D1, D1, "로라뮤직-바인홀", 35.8201, 127.1501);
        assertTrue(matcher.isDuplicate(a, b));
    }

    @Test
    void 같은_제목이라도_다른_날짜_다른_장소면_다른_행사다() {
        SourceRecord base = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        assertFalse(matcher.isDuplicate(base, rec("CULTURE", "c1", "어린왕자", D1.plusDays(1), D1.plusDays(1), "시흥아트센터", 37.37, 126.72)),
                "날짜가 다르다");
        assertFalse(matcher.isDuplicate(base, rec("CULTURE", "c2", "어린왕자", D1, D1, "부산문화회관", 35.15, 129.06)),
                "장소가 멀다");
    }

    @Test
    void 부제가_한쪽에만_붙은_같은_공연은_장소가_같을_때_같은_행사로_본다() {
        SourceRecord kopis = rec("KOPIS", "k1", "부여군충남국악단의 토요상설 국악공연, 유종지미: 끝맺음의 아름다움", D1, D1, "부여국악의전당", 36.27, 126.91);
        SourceRecord culture = rec("CULTURE", "c1", "[부여] 부여군충남국악단의 토요상설 국악공연, 유종지미", D1, D1, "부여국악의전당", 36.27, 126.91);
        assertTrue(matcher.isDuplicate(kopis, culture));
        // 장소가 다르면 포함 관계만으로는 합치지 않는다
        SourceRecord elsewhere = rec("CULTURE", "c2", "[부여] 부여군충남국악단의 토요상설 국악공연, 유종지미", D1, D1, "부산문화회관", 35.15, 129.06);
        assertFalse(matcher.isDuplicate(kopis, elsewhere));
    }

    @Test
    void 종료일이_달라도_기간이_겹치고_시작일이_같으면_같은_행사로_본다() {
        // 장기 공연은 소스마다 종료일을 다르게 준다 (실제: 2026-10-31 vs 2026-12-19)
        SourceRecord culture = rec("CULTURE", "c1", "저항: 찬송이 된 사람들", D1, D1.plusDays(60), "광야아트센터", 37.5239, 127.0396);
        SourceRecord kopis = rec("KOPIS", "k1", "저항: 찬송이 된 사람들", D1, D1.plusDays(120), "광야아트센터", 37.5239, 127.0396);
        assertTrue(matcher.isDuplicate(culture, kopis));
        // 기간이 겹치지 않으면 다른 행사
        SourceRecord later = rec("KOPIS", "k2", "저항: 찬송이 된 사람들", D1.plusDays(200), D1.plusDays(210), "광야아트센터", 37.5239, 127.0396);
        assertFalse(matcher.isDuplicate(culture, later));
    }

    @Test
    void 같은_건물을_다른_이름으로_불러도_같은_시군구에서_제목이_같으면_같은_행사로_본다() {
        SourceRecord a = recIn("KOPIS", "k1", "7시에 만나", D1, "충남도청 문예회관", 36.6811, 126.6710, "44", "810");
        SourceRecord b = recIn("CULTURE", "c1", "[예산] 7시에 만나", D1, "충청남도 문화예술회관", 36.6604, 126.6736, "44", "810");
        assertTrue(matcher.isDuplicate(a, b));
        // 시군구가 다르면 합치지 않는다 (같은 제목의 순회 공연)
        SourceRecord elsewhere = recIn("CULTURE", "c2", "7시에 만나", D1, "장성문화예술회관", 35.3022, 126.7666, "12", "840");
        assertFalse(matcher.isDuplicate(a, elsewhere));
    }

    @Test
    void 같은_소스의_두_레코드는_중복으로_보지_않는다() {
        SourceRecord a = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        SourceRecord b = rec("KOPIS", "k2", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        assertFalse(matcher.isDuplicate(a, b));
    }

    @Test
    void 한쪽에_장소_정보가_전혀_없으면_제목이_거의_같아야만_같은_행사다() {
        SourceRecord withPlace = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        assertTrue(matcher.isDuplicate(withPlace, rec("CULTURE", "c1", "어린왕자", D1, D1, null, null, null)));
        assertFalse(matcher.isDuplicate(withPlace, rec("CULTURE", "c2", "어린왕자 앵콜", D1, D1, null, null, null)));
    }

    // ───────────── 묶음과 병합 ─────────────

    @Test
    void 세_소스의_같은_행사를_하나로_합치고_필드는_우선순위대로_고른다() {
        SourceRecord kopis = with(rec("KOPIS", "k1", "가락 옥토버페스트", D1, D1, "가락몰 하늘공원", 37.4960, 127.1107),
                null, "R석 20,000원", "PAID", "https://kopis/poster.jpg");
        SourceRecord culture = with(rec("CULTURE", "c1", "가락 옥토버페스트", D1, D1, "가락몰 3층 하늘공원", null, null),
                null, "무료", "FREE", "https://culture/thumb.jpg");
        SourceRecord tour = with(rec("TOURAPI", "t1", "가락옥토버페스트", D1, D1, "가락몰 3층 하늘공원", 37.4961, 127.1108),
                "가락시장의 대표 가을 축제", null, "UNKNOWN", "https://tour/image.png");

        MergePlanner.Plan plan = planner.plan(List.of(kopis, culture, tour), Map.of(), UUID::randomUUID);

        assertEquals(1, plan.events().size());
        Event e = plan.events().get(0).event();
        assertEquals(3, e.getSourceCount());
        assertEquals("가락 옥토버페스트", e.getTitle(), "제목은 기본 우선순위(KOPIS)의 것");
        assertEquals("가락시장의 대표 가을 축제", e.getDescription(), "설명은 TourAPI 우선");
        assertTrue(e.isHasDescription());
        assertEquals("https://kopis/poster.jpg", e.getImageUrl(), "이미지는 KOPIS 우선");
        assertEquals("R석 20,000원", e.getPriceText(), "가격은 KOPIS 우선");
        assertEquals("PAID", e.getPriceType());
        assertEquals(37.4960, e.getLat(), 1e-9, "좌표는 KOPIS 우선");
        assertEquals(List.of(1L, 2L, 3L), plan.events().get(0).sourceRecordIds().stream().sorted().toList());
        assertNotNull(e.getDataHash());
    }

    @Test
    void 우선순위_소스에_값이_없으면_다음_소스의_값을_쓴다() {
        SourceRecord kopis = rec("KOPIS", "k1", "가락 옥토버페스트", D1, D1, "가락몰 하늘공원", null, null);
        SourceRecord culture = with(rec("CULTURE", "c1", "가락 옥토버페스트", D1, D1, "가락몰 하늘공원", 37.49, 127.11),
                null, "무료", "FREE", null);
        Event e = planner.plan(List.of(kopis, culture), Map.of(), UUID::randomUUID).events().get(0).event();
        assertEquals("무료", e.getPriceText());
        assertEquals(37.49, e.getLat(), 1e-9);
        assertFalse(e.isHasDescription());
    }

    @Test
    void 합쳐지지_않는_행사는_각각_행사가_된다() {
        SourceRecord a = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        SourceRecord b = rec("KOPIS", "k2", "신데렐라", D1, D1, "시흥아트센터", 37.37, 126.72);
        assertEquals(2, planner.plan(List.of(a, b), Map.of(), UUID::randomUUID).events().size());
    }

    @Test
    void 같은_소스_레코드가_한_묶음에_모이게_되면_연결하지_않고_충돌로_센다() {
        // 문화정보원 한 건이 KOPIS 두 건(같은 제목·날짜·장소)과 모두 맞는 모호한 경우
        SourceRecord k1 = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        SourceRecord k2 = rec("KOPIS", "k2", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        SourceRecord c = rec("CULTURE", "c1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        MergePlanner.Plan plan = planner.plan(List.of(k1, k2, c), Map.of(), UUID::randomUUID);
        assertEquals(2, plan.events().size(), "한 묶음에 KOPIS 가 둘 들어가면 안 된다");
        assertEquals(1, plan.conflicts());
    }

    @Test
    void 시작일_허용_오차를_설정하면_하루_차이도_같은_행사로_본다() {
        MergeProperties tolerant = new MergeProperties(0.85, 0.6, 300, 1, 8, 0.95, 1.0, Map.of("default", List.of("KOPIS", "CULTURE")), 30);
        MergePlanner p = new MergePlanner(new Clusterer(new DuplicateMatcher(tolerant), tolerant), new EventMerger(tolerant));
        SourceRecord a = rec("KOPIS", "k1", "어린왕자", D1, D1.plusDays(5), "시흥아트센터", 37.37, 126.72);
        SourceRecord b = rec("CULTURE", "c1", "어린왕자", D1.plusDays(1), D1.plusDays(5), "시흥아트센터", 37.37, 126.72);
        assertEquals(1, p.plan(List.of(a, b), Map.of(), UUID::randomUUID).events().size());
        // 허용 오차 0 이면 다른 행사
        assertEquals(2, planner.plan(List.of(a, b), Map.of(), UUID::randomUUID).events().size());
    }

    // ───────────── 행사 id 안정성 ─────────────

    @Test
    void 이미_연결된_행사_id를_이어_쓰고_새_레코드가_합류해도_id가_바뀌지_않는다() {
        SourceRecord kopis = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        UUID existing = UUID.randomUUID();
        Map<Long, UUID> links = new HashMap<>(Map.of(kopis.getId(), existing));

        SourceRecord culture = rec("CULTURE", "c1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        Event e = planner.plan(List.of(kopis, culture), links, UUID::randomUUID).events().get(0).event();
        assertEquals(existing, e.getId(), "문화정보원 레코드가 합류해도 기존 id 를 유지한다");
        assertEquals(2, e.getSourceCount());
    }

    @Test
    void 두_행사가_하나로_합쳐지면_작은_id를_쓰고_갈라지면_한쪽만_id를_이어_쓴다() {
        SourceRecord kopis = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        SourceRecord culture = rec("CULTURE", "c1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        UUID small = new UUID(0, 1);
        UUID large = new UUID(0, 2);
        // 합쳐짐: 두 레코드가 각각 다른 행사에 있었다
        Event merged = planner.plan(List.of(kopis, culture), Map.of(kopis.getId(), large, culture.getId(), small), UUID::randomUUID)
                .events().get(0).event();
        assertEquals(small, merged.getId());

        // 갈라짐: 한 행사에 있던 두 레코드가 이제 다른 행사다
        SourceRecord other = rec("CULTURE", "c2", "신데렐라", D1, D1, "부산문화회관", 35.15, 129.06);
        MergePlanner.Plan split = planner.plan(List.of(kopis, other), Map.of(kopis.getId(), small, other.getId(), small), UUID::randomUUID);
        long withOldId = split.events().stream().filter(p -> p.event().getId().equals(small)).count();
        assertEquals(1, withOldId, "같은 id 를 두 행사에 주지 않는다");
    }

    @Test
    void 같은_입력이면_같은_해시를_낸다() {
        SourceRecord a = rec("KOPIS", "k1", "어린왕자", D1, D1, "시흥아트센터", 37.37, 126.72);
        Event e1 = planner.plan(List.of(a), Map.of(), UUID::randomUUID).events().get(0).event();
        Event e2 = planner.plan(List.of(a), Map.of(), UUID::randomUUID).events().get(0).event();
        assertEquals(e1.getDataHash(), e2.getDataHash());
        SourceRecord changed = with(a, "새 설명", null, "UNKNOWN", null);
        assertNotEquals(e1.getDataHash(), planner.plan(List.of(changed), Map.of(), UUID::randomUUID).events().get(0).event().getDataHash());
    }
}
