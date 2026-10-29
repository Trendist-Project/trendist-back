package com.trendist.setting.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.setting.service.SettingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 밸런스 설정(13.3). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {
    private final SettingService settings;

    public record UpdateRequest(String value) {}

    @GetMapping
    public List<SettingService.Item> list() { return settings.list(); }

    @PatchMapping("/{name}")
    public SettingService.Item update(@AuthenticationPrincipal AuthUser user, @PathVariable String name, @RequestBody UpdateRequest req) {
        return settings.update(user.userId(), name, req.value());
    }
}
