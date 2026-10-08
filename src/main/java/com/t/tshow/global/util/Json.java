package com.t.tshow.global.util;

import tools.jackson.databind.json.JsonMapper;

/** JSON 문자열 도우미. JSONB 컬럼에 넣을 값을 만든다 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
