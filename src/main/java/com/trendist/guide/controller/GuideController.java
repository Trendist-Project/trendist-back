package com.trendist.guide.controller;

import com.trendist.guide.dto.GuideDtos;
import com.trendist.guide.service.GuideService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 게임 가이드(4.17). 로그인하지 않아도 볼 수 있다(메인 화면의 [가이드]). */
@RestController
@RequestMapping("/api/guide")
@RequiredArgsConstructor
public class GuideController {
    private final GuideService guideService;

    @GetMapping("/categories")
    public List<GuideDtos.Category> categories() {
        return guideService.categories();
    }

    @GetMapping("/categories/{code}/articles")
    public List<GuideDtos.Article> articles(@PathVariable String code) {
        return guideService.articles(code);
    }
}
