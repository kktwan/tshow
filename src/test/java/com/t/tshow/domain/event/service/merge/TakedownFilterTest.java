package com.t.tshow.domain.event.service.merge;

import com.t.tshow.domain.event.entity.Takedown;
import com.t.tshow.domain.event.entity.TakedownKind;
import com.t.tshow.domain.ingest.entity.SourceRecord;
import com.t.tshow.domain.ingest.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TakedownFilterTest {

    private final TakedownFilter filter = new TakedownFilter();
    private static final LocalDate D = LocalDate.of(2026, 10, 28);

    private static SourceRecord record(SourceType source, String sourceId, String image) {
        return SourceRecord.builder().id((long) sourceId.hashCode()).source(source).sourceId(sourceId).kind("PERFORMANCE")
                .category("theater").title("어린왕자").titleNorm("어린왕자").startDate(D).endDate(D).priceType("UNKNOWN")
                .imageUrl(image).imageLicense(image == null ? null : "Type3").fetchedAt(Instant.now()).build();
    }

    /** 엔티티는 생성자가 막혀 있어 리플렉션으로 만든다 */
    private static Takedown takedown(TakedownKind kind, SourceType source, String sourceId) {
        Takedown t = BeanUtilsHelper.newTakedown();
        ReflectionTestUtils.setField(t, "kind", kind);
        ReflectionTestUtils.setField(t, "source", source);
        ReflectionTestUtils.setField(t, "sourceId", sourceId);
        return t;
    }

    /** protected 생성자로 빈 엔티티를 만든다 */
    private static final class BeanUtilsHelper {
        static Takedown newTakedown() {
            try {
                var c = Takedown.class.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void 삭제_요청이_없으면_그대로다() {
        List<SourceRecord> records = List.of(record(SourceType.KOPIS, "k1", "https://x/a.jpg"));
        TakedownFilter.Result r = filter.apply(records, List.of());
        assertSame(records, r.records());
        assertEquals(0, r.excluded());
    }

    @Test
    void RECORD_요청은_그_소스_레코드만_뺀다() {
        SourceRecord kopis = record(SourceType.KOPIS, "k1", "https://x/a.jpg");
        SourceRecord culture = record(SourceType.CULTURE, "c1", null);
        TakedownFilter.Result r = filter.apply(List.of(kopis, culture), List.of(takedown(TakedownKind.RECORD, SourceType.KOPIS, "k1")));

        assertEquals(List.of(culture), r.records());
        assertEquals(1, r.excluded());
    }

    @Test
    void 같은_id_라도_다른_소스의_레코드는_영향이_없다() {
        SourceRecord other = record(SourceType.CULTURE, "k1", null);
        TakedownFilter.Result r = filter.apply(List.of(other), List.of(takedown(TakedownKind.RECORD, SourceType.KOPIS, "k1")));
        assertEquals(List.of(other), r.records());
    }

    @Test
    void IMAGE_요청은_이미지와_이용_조건만_빼고_나머지는_그대로다() {
        SourceRecord withImage = record(SourceType.TOURAPI, "t1", "https://x/a.jpg");
        TakedownFilter.Result r = filter.apply(List.of(withImage), List.of(takedown(TakedownKind.IMAGE, SourceType.TOURAPI, "t1")));

        SourceRecord out = r.records().get(0);
        assertNull(out.getImageUrl());
        assertNull(out.getImageLicense());
        assertEquals("어린왕자", out.getTitle());
        assertEquals(1, r.imagesRemoved());
        assertEquals("https://x/a.jpg", withImage.getImageUrl(), "원본 엔티티는 바뀌지 않는다");
    }
}
