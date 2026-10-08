package com.t.tshow.domain.ingest.service.normalize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddressCleanTest {

    private final RegionResolver regions = new RegionResolver();

    @Test
    void 같은_주소가_줄임_표기로_한_번_더_붙은_것은_한_번으로_줄인다() {
        assertEquals("서울특별시 종로구 평창30길 40",
                regions.cleanAddress("서울특별시 종로구 평창30길 40 서울 종로구 평창30길 40"));
    }

    @Test
    void 정상_주소는_그대로_둔다() {
        assertEquals("서울특별시 종로구 평창30길 40", regions.cleanAddress("서울특별시 종로구 평창30길 40"));
        assertEquals("경기도 시흥시 승지로 10 서울숲빌딩", regions.cleanAddress("경기도 시흥시 승지로 10 서울숲빌딩"),
                "뒤에 시도 이름이 나와도 앞쪽 끝과 같지 않으면 줄이지 않는다");
        assertNull(regions.cleanAddress(null));
    }
}
