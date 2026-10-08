package com.t.tshow.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 서비스 안내: 무엇을 하는 서비스인지, 데이터 출처와 이용 조건, 이미지, AI, 개인정보 */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("meta", PageMeta.of("tshow 서비스 안내: 데이터 출처, 이미지 이용 조건, AI 추천, 개인정보 처리 안내.", "/about"));
        return "about";
    }
}
