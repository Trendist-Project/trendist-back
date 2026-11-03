package com.trendist.keyword.controller;

import com.trendist.keyword.dto.AiCommentDtos;
import com.trendist.keyword.service.AiCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 AI 한 줄 설명(13.12). /api/admin/**는 ROLE_ADMIN만 들어온다(SecurityConfig). */
@RestController
@RequestMapping("/api/admin/ai-comments")
@RequiredArgsConstructor
public class AdminAiCommentController {
    private final AiCommentService service;

    @GetMapping
    public AiCommentDtos.Page list(@RequestParam(defaultValue = "KR") String country, @RequestParam(defaultValue = "requests") String filter,
                                   @RequestParam(defaultValue = "0") int page) {
        return service.list(country, filter, page);
    }

    @PostMapping("/{keywordId}/regenerate")
    public AiCommentDtos.Row regenerate(@PathVariable long keywordId) { return service.regenerate(keywordId); }

    @PutMapping("/{keywordId}")
    public AiCommentDtos.Row edit(@PathVariable long keywordId, @RequestBody AiCommentDtos.EditRequest req) {
        return service.edit(keywordId, req.comment());
    }

    @PostMapping("/{keywordId}/dismiss")
    public AiCommentDtos.Row dismiss(@PathVariable long keywordId) { return service.dismiss(keywordId); }
}
