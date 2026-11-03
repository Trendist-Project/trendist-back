package com.trendist.keyword.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.service.AiCommentService;
import com.trendist.keyword.service.KeywordActivityService;
import com.trendist.keyword.service.KeywordService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KeywordController {
    private final KeywordService service;
    private final KeywordActivityService activityService;
    private final AiCommentService aiComments;

    @GetMapping("/keywords/search")
    public KeywordDtos.SearchResult search(@AuthenticationPrincipal AuthUser user, @RequestParam String q) {
        return service.search(user.userId(), q);
    }

    @PostMapping("/keywords/plus")
    public KeywordDtos.PlusResult plus(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody KeywordDtos.PlusRequest req) {
        return service.plus(user.userId(), req.text());
    }

    @GetMapping("/keywords/{id}")
    public KeywordDtos.KeywordCard detail(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return service.detail(user.userId(), id);
    }

    /** 활동 그래프(4.12, 9.9). range: 24h / 7d / 30d / 180d. */
    @GetMapping("/keywords/{id}/activity")
    public KeywordDtos.Activity activity(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                         @RequestParam(defaultValue = "24h") String range) {
        return activityService.activity(user.userId(), id, range);
    }

    /** AI 한 줄 설명이 맞지 않다는 갱신 요청(4.12). 관리자가 먼저 접수받고 AI에게 다시 받거나 직접 고친다. */
    @PostMapping("/keywords/{id}/ai-comment/request")
    public void requestAiComment(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        aiComments.request(user.userId(), id);
    }

    @GetMapping("/me/keywords")
    public List<KeywordDtos.MyKeyword> myKeywords(@AuthenticationPrincipal AuthUser user) {
        return service.myKeywords(user.userId());
    }
}
