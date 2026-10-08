package com.t.tshow.index;

import com.t.tshow.event.Event;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 행사 하나의 필터용 값(payload)을 만든다. 날짜·지역·분류·가격처럼 정확히 걸러야 하는 값만 담고,
 * 표시용 내용은 Postgres(원본 저장소)에서 읽는다. 값이 없는 필드는 아예 넣지 않는다.
 *
 * <ul>
 *   <li>start_day / end_day: 날짜를 에포크 일수(정수)로 — 범위 필터용. 종료일이 없으면 시작일과 같다고 본다</li>
 *   <li>location: {lat, lon} 위경도 — 반경 필터용</li>
 * </ul>
 */
@Component
public class PayloadBuilder {

    public static final String KIND = "kind";
    public static final String CATEGORY = "category";
    public static final String SIDO = "sido";
    public static final String SIGUNGU = "sigungu";
    public static final String START_DAY = "start_day";
    public static final String END_DAY = "end_day";
    public static final String PRICE_TYPE = "price_type";
    public static final String HAS_DESCRIPTION = "has_description";
    public static final String SOURCE_COUNT = "source_count";
    public static final String LOCATION = "location";

    public Map<String, Object> build(Event e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(KIND, e.kind());
        p.put(CATEGORY, e.category());
        if (e.sidoCode() != null) p.put(SIDO, e.sidoCode());
        if (e.sigunguCode() != null) p.put(SIGUNGU, e.sigunguCode());
        if (e.startDate() != null) {
            p.put(START_DAY, e.startDate().toEpochDay());
            p.put(END_DAY, (e.endDate() != null ? e.endDate() : e.startDate()).toEpochDay());
        }
        p.put(PRICE_TYPE, e.priceType());
        p.put(HAS_DESCRIPTION, e.hasDescription());
        p.put(SOURCE_COUNT, (long) e.sourceCount());
        if (e.lat() != null && e.lon() != null) {
            Map<String, Object> geo = new LinkedHashMap<>();
            geo.put("lat", e.lat());
            geo.put("lon", e.lon());
            p.put(LOCATION, geo);
        }
        return p;
    }
}
