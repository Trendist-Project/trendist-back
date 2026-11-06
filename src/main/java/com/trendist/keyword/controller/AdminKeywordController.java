package com.trendist.keyword.controller;

import com.trendist.keyword.service.KeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 키워드(13.7). /api/admin/**는 ROLE_ADMIN만 들어온다(SecurityConfig). 나라를 주지 않으면 KR이다. */
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
                                                @RequestParam(defaultValue = "createdAt") String sort,
                                                @RequestParam(defaultValue = "desc") String dir,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return keywordService.adminSearch(country, state, source, q, sort, dir, page, size);
    }

    /** 한 키워드 조회: 등록 전이어도 규칙에 맞는지와 기존 행을 돌려준다. 화면이 [바로 추가]·[바로 금지] 카드를 그린다(13.6). */
    @GetMapping("/lookup")
    public KeywordService.AdminKeywordLookup lookup(@RequestParam(defaultValue = "KR") String country, @RequestParam(defaultValue = "") String text) {
        return keywordService.adminLookup(country, text);
    }

    public record BanTextRequest(String text, String country) {}

    /** 등록 전 단어도 바로 금지한다(없으면 금지 상태로 새로 등록). */
    @PostMapping("/ban-text")
    public KeywordService.AdminKeywordRow banText(@RequestBody BanTextRequest req) {
        return keywordService.adminBanText(req.country() == null ? "KR" : req.country(), req.text() == null ? "" : req.text());
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
