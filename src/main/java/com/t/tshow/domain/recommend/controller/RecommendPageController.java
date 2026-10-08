package com.t.tshow.domain.recommend.controller;

import com.t.tshow.domain.recommend.dto.AiRecommendResponse;
import com.t.tshow.domain.recommend.service.RecommendService;
import com.t.tshow.domain.search.dto.SearchRequest;
import com.t.tshow.global.exception.BusinessException;
import com.t.tshow.global.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** 검색 화면의 AI 추천 영역. 전체 화면이 아니라 그 영역의 HTML 조각만 돌려주고, 스크립트가 화면에 끼워 넣는다 */
@Controller
public class RecommendPageController {

    private final RecommendService recommend;

    public RecommendPageController(RecommendService recommend) {
        this.recommend = recommend;
    }

    @GetMapping("/recommend")
    public String recommend(SearchRequest request, HttpServletRequest http, HttpServletResponse response, Model model) {
        AiRecommendResponse result;
        try {
            result = recommend.recommend(request, ClientIp.of(http));
        } catch (BusinessException e) {
            response.setStatus(e.errorCode().status().value());
            result = AiRecommendResponse.notice(e.getMessage(), List.of());
        }
        model.addAttribute("ai", result);
        return "ai-result :: result";
    }
}
