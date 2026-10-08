package com.t.tshow.support;

import com.t.tshow.global.config.IngestProperties;
import com.t.tshow.global.config.NormalizeProperties;


import java.util.List;

/** 테스트에서 쓰는 기본 설정 객체 (Spring 컨텍스트 없이 만든다) */
public final class TestProperties {

    private TestProperties() {
    }

    public static IngestProperties ingest() {
        IngestProperties.Source source = new IngestProperties.Source("http://source.test", 30, 100, 0, 0, 0, 5, 0);
        return new IngestProperties(6, 24, false, "0 0 3 * * *", "Asia/Seoul", "tshow",
                List.of("KOPIS", "CULTURE", "TOURAPI"), List.of("yyyy.MM.dd", "yyyyMMdd", "yyyy-MM-dd"), true, 3, 0.5,
                source, source, source);
    }

    public static NormalizeProperties normalize() {
        return new NormalizeProperties(
                List.of("\\[[^\\]]*\\]", "\\([^)]*\\)", "(?<!\\d)\\d{8}(?!\\d)"),
                List.of("\\([^)]*\\)"));
    }
}
