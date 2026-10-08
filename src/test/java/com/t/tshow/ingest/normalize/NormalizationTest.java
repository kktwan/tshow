package com.t.tshow.ingest.normalize;

import com.t.tshow.ingest.TestProperties;
import com.t.tshow.ingest.source.SourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationTest {

    private final CategoryResolver categories = new CategoryResolver();
    private final RegionResolver regions = new RegionResolver();
    private final PriceClassifier prices = new PriceClassifier();
    private final TextNormalizer texts = new TextNormalizer(TestProperties.normalize());
    private final DateParser dates = new DateParser(TestProperties.ingest());

    @Test
    void 소스별_분류를_표준_분류로_바꾼다() {
        NormalizationReport report = new NormalizationReport();
        assertEquals("classical", categories.resolve(SourceType.KOPIS, "서양음악(클래식)", report).id());
        assertEquals("traditional", categories.resolve(SourceType.CULTURE, "국악", report).id());
        assertEquals("exhibition", categories.resolve(SourceType.TOURAPI, "EV030100", report).id());
        assertEquals("EXHIBITION", categories.resolve(SourceType.CULTURE, "전시", report).kind());
        assertTrue(report.isEmpty());
    }

    @Test
    void 모르는_분류는_기본_분류로_저장하고_리포트에_남긴다() {
        NormalizationReport report = new NormalizationReport();
        CategoryResolver.Category c = categories.resolve(SourceType.KOPIS, "새로운장르", report);
        assertEquals("performance_other", c.id());
        assertEquals(1, report.unmapped().get("category:KOPIS").get("새로운장르"));
    }

    @Test
    void 지역_이름과_별칭을_코드로_바꾼다() {
        NormalizationReport report = new NormalizationReport();
        assertEquals("11", regions.resolve("서울특별시", null, null, null, null, report).sidoCode());
        assertEquals("11", regions.resolve("서울", "용산구", null, null, null, report).sidoCode());
        assertEquals("11", regions.resolve("서울시", null, null, null, null, report).sidoCode());
        // 광주광역시·전라남도는 전남광주통합특별시(12)로 통합됨 — 별칭 파일로 처리
        assertEquals("12", regions.resolve("광주", null, null, null, null, report).sidoCode());
        assertEquals("12", regions.resolve("전남광주통합특별시", null, null, null, null, report).sidoCode());
        assertEquals("36110", regions.resolve("세종특별자치시", null, null, null, null, report).sidoCode());
        assertEquals("52", regions.resolve("전북", null, null, null, null, report).sidoCode());
        assertTrue(report.isEmpty(), report.unmapped().toString());
    }

    @Test
    void 코드가_오면_코드를_우선한다() {
        NormalizationReport report = new NormalizationReport();
        RegionResolver.Region r = regions.resolve(null, null, null, "11", "710", report);
        assertEquals("11", r.sidoCode());
        assertEquals("710", r.sigunguCode());
        assertEquals("36110", regions.resolve(null, null, null, "36110", null, report).sidoCode());
    }

    @Test
    void 주소에서_시군구를_찾는다() {
        NormalizationReport report = new NormalizationReport();
        RegionResolver.Region r = regions.resolve("경기도", null, "경기도 시흥시 서울대학로 255 (배곧동)", null, null, report);
        assertEquals("41", r.sidoCode());
        assertNotNull(r.sigunguCode());
        RegionResolver.Region fromAddressOnly = regions.resolve(null, null, "서울특별시 용산구 서빙고로 137", null, null, report);
        assertEquals("11", fromAddressOnly.sidoCode());
        assertEquals("170", fromAddressOnly.sigunguCode());
    }

    @Test
    void 해외는_오류가_아니라_지역_없음이고_모르는_지역은_리포트에_남긴다() {
        NormalizationReport report = new NormalizationReport();
        assertEquals(RegionResolver.Region.NONE, regions.resolve("해외", null, null, null, null, report));
        assertTrue(report.isEmpty());
        assertEquals(RegionResolver.Region.NONE, regions.resolve("화성남도", null, null, null, null, report));
        assertEquals(1, report.unmapped().get("region").get("화성남도"));
    }

    @Test
    void 가격_문장에서_무료_유료를_가른다() {
        assertEquals(PriceClassifier.PriceType.FREE, prices.classify("무료"));
        assertEquals(PriceClassifier.PriceType.FREE, prices.classify("전석 무료"));
        assertEquals(PriceClassifier.PriceType.PAID, prices.classify("R석 20,000원, S석 10,000원"));
        assertEquals(PriceClassifier.PriceType.PAID, prices.classify("유아 무료, 성인 10,000원"));
        assertEquals(PriceClassifier.PriceType.UNKNOWN, prices.classify("현장 문의"));
        assertEquals(PriceClassifier.PriceType.UNKNOWN, prices.classify(" "));
    }

    @Test
    void 같은_공연의_표기_차이를_같은_제목으로_본다() {
        assertEquals(texts.title("[오산] 모래로 그린 음악동화: 어린왕자 "), texts.title("모래로 그린 음악동화: 어린왕자 [오산]"));
        assertEquals(texts.title("[대학로] 히든퍼즐 20260701"), texts.title("히든퍼즐"));
        assertNotEquals(texts.title("어린왕자"), texts.title("신데렐라"));
        assertEquals(texts.venue("시흥아트센터  (대공연장)"), texts.venue("시흥아트센터 "));
    }

    @Test
    void 소스마다_다른_날짜_형식을_읽는다() {
        assertEquals(LocalDate.of(2026, 10, 21), dates.parse("2026.10.21"));
        assertEquals(LocalDate.of(2026, 10, 16), dates.parse("20261016"));
        assertEquals(LocalDate.of(2026, 10, 16), dates.parse("2026-10-16"));
        assertNull(dates.parse(""));
        assertNull(dates.parse("미정"));
    }
}
