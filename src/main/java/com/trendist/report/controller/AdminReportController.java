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

    /** 신고에서 바로 제재·댓글 삭제를 하고 조치함으로 넘긴다(13.5). */
    @PostMapping("/{id}/act")
    public ReportService.ActResult act(@PathVariable long id, @RequestBody ReportService.ActRequest req) {
        return service.adminAct(id, req);
    }

    @PatchMapping("/{id}")
    public Map<String, Integer> setStatus(@PathVariable long id, @RequestBody StatusRequest req) {
        return Map.of("changed", service.adminSetStatus(id, req.status(), req.sameTarget()));
    }
}
