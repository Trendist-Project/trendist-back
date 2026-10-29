package com.trendist.player.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.service.ScoreHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScoreHistoryController {
    private final ScoreHistoryService service;

    /** 내 점수 추이(4.15). range: 24h / 7d / 30d / all. */
    @GetMapping("/me/score-history")
    public PlayerDtos.ScoreHistory history(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "24h") String range) {
        return service.history(user.userId(), range);
    }
}
