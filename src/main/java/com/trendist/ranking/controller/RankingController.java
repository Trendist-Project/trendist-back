package com.trendist.ranking.controller;

import com.trendist.ranking.dto.RankingEntry;
import com.trendist.ranking.service.RankingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RankingController {
    private final RankingService rankingService;

    /** 내 순위(4.14): 지금 방에 접속 중이면 {rank, score …}, 아니면 204. 리더보드에 안 보이는 순위도 알려 준다 */
    @GetMapping("/rankings/me")
    public org.springframework.http.ResponseEntity<RankingEntry> mine(@org.springframework.security.core.annotation.AuthenticationPrincipal com.trendist.auth.security.AuthUser user) {
        RankingEntry e = rankingService.mine(user.userId());
        return e == null ? org.springframework.http.ResponseEntity.noContent().build() : org.springframework.http.ResponseEntity.ok(e);
    }

    /** 현재 유행도 TOP 10(4.14). 몇 위까지 볼지는 화면에서 사람마다 고른다. */
    @GetMapping("/rankings")
    public List<RankingEntry> rankings(@RequestParam(defaultValue = "KR") String country) {
        return rankingService.top(country);
    }
}
