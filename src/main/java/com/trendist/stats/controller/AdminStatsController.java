package com.trendist.stats.controller;

import com.trendist.stats.dto.StatsDtos;
import com.trendist.stats.service.StatsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 통계(13.9). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {
    private final StatsService stats;

    @GetMapping("/summary")
    public StatsDtos.Summary summary(@RequestParam(defaultValue = "KR") String country) { return stats.summary(country); }

    @GetMapping("/hourly")
    public List<StatsDtos.Row> hourly(@RequestParam(defaultValue = "KR") String country, @RequestParam(defaultValue = "24") int hours) {
        return stats.hourly(country, hours);
    }

    @GetMapping("/daily")
    public List<StatsDtos.Row> daily(@RequestParam(defaultValue = "KR") String country, @RequestParam(defaultValue = "30") int days) {
        return stats.daily(country, days);
    }
}
