package com.trendist.integration.controller;

import com.trendist.integration.dto.IntegrationDtos;
import com.trendist.integration.service.IntegrationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 연동 바로가기(13.15). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/integrations")
@RequiredArgsConstructor
public class AdminIntegrationController {
    private final IntegrationService integrations;

    @GetMapping
    public List<IntegrationDtos.Item> list() { return integrations.list(); }

    @PatchMapping("/{code}")
    public IntegrationDtos.Item update(@PathVariable String code, @RequestBody IntegrationDtos.Request req) {
        return integrations.updateUrl(code, req.url());
    }
}
