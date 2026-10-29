package com.trendist.report.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.report.dto.ReportRequest;
import com.trendist.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 신고(13.5). 게스트도 할 수 있다. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ReportRequest req) {
        service.report(user.userId(), req.target(), req.targetId(), req.reason());
    }
}
