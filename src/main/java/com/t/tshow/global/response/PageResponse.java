package com.t.tshow.global.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 목록 API 의 공통 페이지 형식 (page 는 0부터) */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
