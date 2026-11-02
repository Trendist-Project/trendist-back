package com.trendist.dashboard.controller;

import com.trendist.dashboard.dto.DashboardDtos;
import com.trendist.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 대시보드(13.11). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final DashboardService service;

    @GetMapping
    public DashboardDtos.Dashboard dashboard(@RequestParam(defaultValue = "KR") String country) { return service.dashboard(country); }
}
