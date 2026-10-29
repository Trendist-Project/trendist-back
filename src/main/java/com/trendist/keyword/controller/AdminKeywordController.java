package com.trendist.keyword.controller;

import com.trendist.keyword.service.KeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 키워드(13.7). /api/admin/**는 ROLE_ADMIN만 들어온다(SecurityConfig). 지금은 KR만 연다. */
@RestController
@RequestMapping("/api/admin/keywords")
@RequiredArgsConstructor
public class AdminKeywordController {
    private final KeywordService keywordService;

    public record AddRequest(String text, String country) {}

    /** 키워드 목록: 상태·반영 소스·검색어로 거르고 정렬한다(13.6). */
    @GetMapping
    public KeywordService.AdminKeywordPage list(@RequestParam(defaultValue = "KR") String country,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String source,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "trendValue") String sort,
                                                @RequestParam(defaultValue = "desc") String dir,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return keywordService.adminSearch(country, state, source, q, sort, dir, page, size);
    }

    @PostMapping("/{id}/ban")
    public KeywordService.AdminKeywordRow ban(@PathVariable long id) { return keywordService.adminBan(id, true); }

    @PostMapping("/{id}/unban")
    public KeywordService.AdminKeywordRow unban(@PathVariable long id) { return keywordService.adminBan(id, false); }

    @PostMapping
    public KeywordService.AdminKeyword add(@RequestBody AddRequest req) {
        return keywordService.adminAdd(req.country() == null ? "KR" : req.country(), req.text() == null ? "" : req.text());
    }
}
