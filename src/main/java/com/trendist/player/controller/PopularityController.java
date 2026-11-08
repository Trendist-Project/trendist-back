package com.trendist.player.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.service.PlayerInfoService;
import com.trendist.player.service.PopularityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 플레이어 검색과 인기도(4.34) */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PopularityController {
    private final PopularityService service;
    private final PlayerInfoService info;

    @GetMapping("/search")
    public List<PlayerDtos.PlayerHit> search(@AuthenticationPrincipal AuthUser user, @RequestParam String q) {
        return service.search(user.userId(), q);
    }

    /** 검색 결과의 상세 보기(접속 중이 아니어도 같은 나라면 본다) */
    @GetMapping("/{playerId}")
    public PlayerDtos.PlayerCard card(@AuthenticationPrincipal AuthUser user, @PathVariable long playerId) {
        return info.cardById(user.userId(), playerId);
    }

    @PostMapping("/{playerId}/popularity")
    public PlayerDtos.PopularityResult vote(@AuthenticationPrincipal AuthUser user, @PathVariable long playerId,
                                            @RequestBody PlayerDtos.PopularityRequest body) {
        return service.vote(user.userId(), playerId, body.value());
    }
}
