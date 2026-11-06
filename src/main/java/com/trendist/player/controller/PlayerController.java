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
    @Transactional
    public PlayerDtos.Me me(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String country) {
        // country가 있으면 그 나라로 고르고(마지막 플레이 지역도 바뀐다), 없으면 마지막으로 플레이한 지역
        var found = country == null || country.isBlank() ? playerService.findCurrent(user.userId())
                : playerService.selectCountry(user.userId(), country);
        var p = found.map(playerService::summary).orElse(null);
        return new PlayerDtos.Me(user.userId(), playerService.isGuest(user.userId()), p);
    }

    /** 닉네임 정하기(첫 입장). */
    @PostMapping("/players")
    @Transactional
    public PlayerDtos.PlayerSummary create(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody PlayerDtos.CreatePlayerRequest req) {
        return playerService.summary(playerService.create(user.userId(), req.nickname(), req.countryCode()));
    }

    /** 현재 나라 닉네임 바꾸기(4.1). */
    @PatchMapping("/me/nickname")
    @Transactional
    public PlayerDtos.PlayerSummary changeNickname(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody PlayerDtos.ChangeNicknameRequest req) {
        return playerService.summary(playerService.changeNickname(user.userId(), req.nickname()));
    }
}
