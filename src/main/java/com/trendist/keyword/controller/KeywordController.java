package com.trendist.keyword.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.service.KeywordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class KeywordController {
    private final KeywordService service;

    public KeywordController(KeywordService service) { this.service = service; }

    @GetMapping("/keywords/search")
    public KeywordDtos.SearchResult search(@AuthenticationPrincipal AuthUser user, @RequestParam String q) {
        return service.search(user.userId(), q);
    }

    @PostMapping("/keywords/plus")
    public KeywordDtos.PlusResult plus(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody KeywordDtos.PlusRequest req) {
        return service.plus(user.userId(), req.text());
    }

    @GetMapping("/me/keywords")
    public List<KeywordDtos.MyKeyword> myKeywords(@AuthenticationPrincipal AuthUser user) {
        return service.myKeywords(user.userId());
    }
}
