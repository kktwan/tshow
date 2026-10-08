package com.t.tshow.domain.event.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageLicenseTest {

    @Test
    void 공공누리_유형을_해석하고_변경금지_유형을_가려낸다() {
        assertTrue(ImageLicense.noModify("Type3"), "제3유형은 출처표시+변경금지");
        assertTrue(ImageLicense.noModify("type4"));
        assertFalse(ImageLicense.noModify("Type1"));
        assertFalse(ImageLicense.noModify("Type2"));
        assertEquals("공공누리 제3유형(출처표시·변경금지)", ImageLicense.of("Type3").label());
        assertEquals("공공누리 제1유형(출처표시)", ImageLicense.of(" Type1 ").label());
    }

    @Test
    void 모르는_값이나_없는_값은_제한이_없는_것으로_다루지_않고_해석하지_않는다() {
        assertNull(ImageLicense.of(null));
        assertNull(ImageLicense.of(""));
        assertNull(ImageLicense.of("CC-BY"));
        assertFalse(ImageLicense.noModify(null));
    }
}
