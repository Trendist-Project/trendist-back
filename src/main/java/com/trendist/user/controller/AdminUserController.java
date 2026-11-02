package com.trendist.user.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.user.dto.AdminUserDtos;
import com.trendist.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 사용자 관리(13.10). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    public AdminUserDtos.Page search(@RequestParam(defaultValue = "KR") String country, @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "seen") String sort,
                                     @RequestParam(defaultValue = "0") int page) {
        return service.search(country, q, filter, sort, page);
    }

    @GetMapping("/{userId}")
    public AdminUserDtos.Detail detail(@PathVariable long userId) { return service.detail(userId); }

    @PostMapping("/{userId}/sanctions")
    public AdminUserDtos.Detail sanction(@PathVariable long userId, @RequestBody AdminUserDtos.SanctionRequest req) {
        return service.sanction(userId, req);
    }

    @PostMapping("/sanctions/{sanctionId}/release")
    public AdminUserDtos.Detail release(@PathVariable long sanctionId) { return service.release(sanctionId); }

    @PatchMapping("/{userId}/nickname")
    public AdminUserDtos.Detail rename(@PathVariable long userId, @RequestBody AdminUserDtos.NicknameRequest req) {
        return service.rename(userId, req.nickname());
    }

    @PostMapping("/{userId}/look/reset")
    public AdminUserDtos.Detail resetLook(@PathVariable long userId) { return service.resetLook(userId); }

    @PatchMapping("/{userId}/role")
    public AdminUserDtos.Detail role(@AuthenticationPrincipal AuthUser actor, @PathVariable long userId, @RequestBody AdminUserDtos.RoleRequest req) {
        return service.setAdmin(actor.userId(), userId, req.admin());
    }

    @PostMapping("/{userId}/kick")
    public ResponseEntity<Void> kick(@PathVariable long userId) {
        service.kick(userId);
        return ResponseEntity.noContent().build();
    }
}
