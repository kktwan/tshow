package com.t.tshow.domain.event.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventDetailTest {

    private static EventDetail withPhone(String phone) {
        return new EventDetail(null, null, null, null, null, "제목", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, false, null, null, phone, List.of(), List.of());
    }

    @Test
    void 문의_글에서_전화번호를_찾아_걸_수_있는_주소로_만든다() {
        assertEquals("tel:0220779000", withPhone("국립중앙박물관 02-2077-9000").telHref());
        assertEquals("tel:0316442100", withPhone("031-644-2100").telHref());
        assertEquals("tel:15881234", withPhone("1588-1234").telHref());
        assertEquals("tel:01012345678", withPhone("담당자 010 1234 5678".replace(' ', '-')).telHref());
    }

    @Test
    void 전화번호가_없거나_없는_값이면_링크를_만들지_않는다() {
        assertNull(withPhone("현장 문의").telHref());
        assertNull(withPhone(null).telHref());
    }
}
