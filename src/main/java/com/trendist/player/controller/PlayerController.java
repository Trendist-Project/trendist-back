package com.trendist.player.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerService playerService;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public PlayerDtos.Me me(@AuthenticationPrincipal AuthUser user) {
        var p = playerService.findCurrent(user.userId()).map(playerService::summary).orElse(null);
        return new PlayerDtos.Me(user.userId(), true, p);
    }

    /** 닉네임 정하기(첫 입장). */
    @PostMapping("/players")
    @Transactional
    public PlayerDtos.PlayerSummary create(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody PlayerDtos.CreatePlayerRequest req) {
        return playerService.summary(playerService.create(user.userId(), req.nickname(), req.countryCode()));
    }
}
