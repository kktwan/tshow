package com.t.tshow.domain.index.service;

import com.t.tshow.domain.event.entity.Event;
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
        p.put(KIND, e.getKind());
        p.put(CATEGORY, e.getCategory());
        if (e.getSidoCode() != null) p.put(SIDO, e.getSidoCode());
        if (e.getSigunguCode() != null) p.put(SIGUNGU, e.getSigunguCode());
        if (e.getStartDate() != null) {
            p.put(START_DAY, e.getStartDate().toEpochDay());
            p.put(END_DAY, (e.getEndDate() != null ? e.getEndDate() : e.getStartDate()).toEpochDay());
        }
        p.put(PRICE_TYPE, e.getPriceType());
        p.put(HAS_DESCRIPTION, e.isHasDescription());
        p.put(SOURCE_COUNT, (long) e.getSourceCount());
        if (e.getLat() != null && e.getLon() != null) {
            Map<String, Object> geo = new LinkedHashMap<>();
            geo.put("lat", e.getLat());
            geo.put("lon", e.getLon());
            p.put(LOCATION, geo);
        }
        return p;
    }
}
