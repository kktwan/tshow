package com.t.tshow.domain.recommend.controller;

import com.t.tshow.domain.recommend.dto.AiRecommendResponse;
import com.t.tshow.domain.recommend.service.RecommendService;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.global.response.ApiResponse;
import com.t.tshow.global.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommend")
public class RecommendApiController {

    private final RecommendService recommend;

    public RecommendApiController(RecommendService recommend) {
        this.recommend = recommend;
    }

    /** 검색과 같은 조건을 받아 AI 가 후보 중에서 고른 행사와 이유를 돌려준다. 하루 사용 한도가 있다 */
    @GetMapping
    public ApiResponse<AiRecommendResponse> recommend(SearchRequest request, HttpServletRequest http) {
        return ApiResponse.success(recommend.recommend(request, ClientIp.of(http)));
    }
}
