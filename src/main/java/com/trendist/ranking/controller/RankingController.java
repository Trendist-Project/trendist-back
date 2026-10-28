package com.trendist.ranking.controller;

import com.trendist.ranking.dto.RankingEntry;
import com.trendist.ranking.service.RankingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RankingController {
    private final RankingService rankingService;

    public RankingController(RankingService rankingService) { this.rankingService = rankingService; }

    /** 현재 유행도 TOP 10(4.14). */
    @GetMapping("/rankings")
    public List<RankingEntry> rankings(@RequestParam(defaultValue = "KR") String country) {
        return rankingService.top(country);
    }
}
