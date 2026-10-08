package com.t.tshow.ingest.normalize;

import com.t.tshow.global.config.IngestProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 소스마다 다른 날짜 문자열(2026.10.21 / 20261016 / 2026-10-16)을 날짜로 바꾼다. 형식 목록은 설정에서 온다. */
@Component
public class DateParser {

    private final List<DateTimeFormatter> formatters;

    public DateParser(IngestProperties properties) {
        this.formatters = properties.dateFormats().stream().map(DateTimeFormatter::ofPattern).toList();
    }

    /** 비었거나 어떤 형식으로도 읽을 수 없으면 null */
    public LocalDate parse(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.trim();
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDate.parse(value, f);
            } catch (DateTimeParseException ignored) {
                // 다음 형식을 시도한다
            }
        }
        return null;
    }
}
