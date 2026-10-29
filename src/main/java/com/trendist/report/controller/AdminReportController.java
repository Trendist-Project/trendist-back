package com.trendist.report.controller;

import com.trendist.report.service.ReportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 신고 검토(13.5). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final ReportService service;

    public record StatusRequest(String status, boolean sameTarget) {}

    @GetMapping
    public ReportService.AdminReportPage list(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        return service.adminList(status, page, size);
    }

    @PatchMapping("/{id}")
    public Map<String, Integer> setStatus(@PathVariable long id, @RequestBody StatusRequest req) {
        return Map.of("changed", service.adminSetStatus(id, req.status(), req.sameTarget()));
    }
}
