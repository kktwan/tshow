package com.t.tshow.domain.search.controller;

import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.domain.search.dto.SearchResponse;
import com.t.tshow.domain.search.service.SearchService;
import com.t.tshow.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private final SearchService search;

    public SearchApiController(SearchService search) {
        this.search = search;
    }

    /** 검색어 속 조건(날짜·지역·무료·분류)과 화면 선택 조건을 먼저 걸고, 남은 문장으로 의미가 비슷한 순서로 찾는다 */
    @GetMapping
    public ApiResponse<SearchResponse> search(SearchRequest request) {
        return ApiResponse.success(search.search(request));
    }
}
