package com.trendist.player.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.service.PlayerInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 같은 방 다른 플레이어(4.11). 사용자 번호가 아니라 방 안의 세션 엔티티 번호로 가리킨다. */
@RestController
@RequestMapping("/api/room/players")
@RequiredArgsConstructor
public class RoomPlayerController {
    private final PlayerInfoService service;

    @GetMapping("/{entityNo}")
    public PlayerDtos.PlayerCard card(@AuthenticationPrincipal AuthUser user, @PathVariable int entityNo) {
        return service.card(user.userId(), entityNo);
    }

    /** 다른 사람은 최근 7일만 볼 수 있다(4.15). range는 7d만 받는다. */
    @GetMapping("/{entityNo}/score-history")
    public PlayerDtos.ScoreHistory history(@AuthenticationPrincipal AuthUser user, @PathVariable int entityNo,
                                           @RequestParam(defaultValue = "7d") String range) {
        if (!"7d".equals(range)) throw com.trendist.common.exception.ApiException.badRequest("SCORE_RANGE", "다른 사람의 점수 추이는 7일만 볼 수 있어요");
        return service.week(user.userId(), entityNo);
    }
}
