package com.t.tshow.ingest.dedup;

import com.t.tshow.global.config.MergeProperties;
import com.t.tshow.ingest.SourceRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 서로 다른 소스의 두 레코드가 같은 행사인지 판정한다. 기준값은 {@link MergeProperties}에서 온다.
 * 같은 소스의 두 레코드는 (같은 작품의 다른 공연장·기간 공연일 수 있어서) 중복으로 보지 않는다.
 *
 * <p>판정: 시작일이 같고(허용 오차 안, 기간은 겹치면 된다), 제목이 충분히 비슷하고, 장소가 같다(이름이 비슷하거나 좌표가 가깝다).
 * 장소가 맞지 않아도 같은 시군구에서 제목이 사실상 같으면(같은 건물의 다른 이름) 같은 행사로 본다.
 * 한쪽에 장소 정보가 전혀 없으면 더 엄격한 제목 유사도를 요구한다.
 */
@Component
public class DuplicateMatcher {

    /** 장소 이름 포함 비교에서 우연히 맞는 것을 막기 위한 최소 글자 수 */
    private static final int MIN_CONTAINS_LENGTH = 3;

    private final MergeProperties properties;

    public DuplicateMatcher(MergeProperties properties) {
        this.properties = properties;
    }

    public boolean isDuplicate(SourceRecord a, SourceRecord b) {
        if (a.source().equals(b.source())) return false;
        if (!samePeriod(a, b)) return false;

        double titleSimilarity = Similarity.dice(a.titleNorm(), b.titleNorm());
        // 부제가 한쪽에만 붙은 경우(한쪽 제목이 다른 쪽에 통째로 포함)도 제목이 맞는 것으로 본다. 단 장소가 같을 때만 최종 인정한다
        boolean titleContained = Similarity.containsEither(a.titleNorm(), b.titleNorm(), properties.containedTitleMinLength());
        if (titleSimilarity < properties.titleSimilarity() && !titleContained) return false;

        Boolean samePlace = samePlace(a, b);
        if (samePlace == null) {
            // 장소를 비교할 수 없으면 제목이 거의 같을 때만 같은 행사로 본다 (포함 관계는 인정하지 않는다)
            return titleSimilarity >= properties.noPlaceTitleSimilarity();
        }
        if (samePlace) return true;
        return sameSigungu(a, b) && titleSimilarity >= properties.sameRegionTitleSimilarity();
    }

    /** 시작일이 허용 오차 안이고 기간이 겹친다. 종료일은 소스마다 달라서 같을 필요가 없다 */
    private boolean samePeriod(SourceRecord a, SourceRecord b) {
        LocalDate x = a.startDate();
        LocalDate y = b.startDate();
        if (x == null || y == null) return x == null && y == null;
        if (Math.abs(ChronoUnit.DAYS.between(x, y)) > properties.dateToleranceDays()) return false;
        if (a.endDate() == null || b.endDate() == null) return true;
        return !x.isAfter(b.endDate()) && !y.isAfter(a.endDate());
    }

    private static boolean sameSigungu(SourceRecord a, SourceRecord b) {
        return a.sidoCode() != null && a.sigunguCode() != null
                && a.sidoCode().equals(b.sidoCode()) && a.sigunguCode().equals(b.sigunguCode());
    }

    /** 같은 장소면 true, 다른 장소면 false, 비교할 정보가 없으면 null */
    private Boolean samePlace(SourceRecord a, SourceRecord b) {
        boolean namesKnown = notEmpty(a.venueNameNorm()) && notEmpty(b.venueNameNorm());
        boolean coordsKnown = a.lat() != null && a.lon() != null && b.lat() != null && b.lon() != null;
        if (!namesKnown && !coordsKnown) return null;

        if (namesKnown) {
            if (Similarity.dice(a.venueNameNorm(), b.venueNameNorm()) >= properties.venueSimilarity()
                    || Similarity.containsEither(a.venueNameNorm(), b.venueNameNorm(), MIN_CONTAINS_LENGTH)) {
                return true;
            }
        }
        if (coordsKnown) {
            return Similarity.distanceMeters(a.lat(), a.lon(), b.lat(), b.lon()) <= properties.maxDistanceMeters();
        }
        return false;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
