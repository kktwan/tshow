package com.t.tshow.domain.ingest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 제공처가 지운 항목을 가려낼 때, API 장애로 목록이 비거나 줄었을 때 대량 삭제가 일어나지 않는지 확인한다 */
class SourceRecordReconcileTest {

    @Test
    void 있어야_할_항목이_없으면_판단할_게_없어_진행해도_된다() {
        assertTrue(SourceRecordService.isReliable(0, 0, 0.5));
    }

    @Test
    void 목록이_충분하면_믿는다() {
        assertTrue(SourceRecordService.isReliable(1000, 900, 0.5));
        assertTrue(SourceRecordService.isReliable(1000, 500, 0.5), "정확히 기준 비율이면 믿는다");
    }

    @Test
    void 목록이_비었거나_기준보다_적으면_장애로_보고_삭제_판단을_건너뛴다() {
        assertFalse(SourceRecordService.isReliable(1000, 0, 0.5), "빈 목록은 장애");
        assertFalse(SourceRecordService.isReliable(1000, 499, 0.5));
        assertFalse(SourceRecordService.isReliable(3, 0, 0.0), "비율이 0 이어도 빈 목록은 믿지 않는다");
    }
}
