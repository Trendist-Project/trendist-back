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
                                     @RequestParam(defaultValue = "desc") String dir,
                                     @RequestParam(defaultValue = "0") int page) {
        return service.search(country, q, filter, sort, dir, page);
    }

    @GetMapping("/{userId}")
    public AdminUserDtos.Detail detail(@PathVariable long userId, @RequestParam(required = false) String country) {
        return service.detail(userId, country);
    }

    @PostMapping("/{userId}/sanctions")
    public AdminUserDtos.Detail sanction(@PathVariable long userId, @RequestBody AdminUserDtos.SanctionRequest req,
                                         @RequestParam(required = false) String country) {
        return service.sanction(userId, req, country);
    }

    @PostMapping("/sanctions/{sanctionId}/release")
    public AdminUserDtos.Detail release(@PathVariable long sanctionId, @RequestParam(required = false) String country) {
        return service.release(sanctionId, country);
    }

    @PatchMapping("/{userId}/nickname")
    public AdminUserDtos.Detail rename(@PathVariable long userId, @RequestBody AdminUserDtos.NicknameRequest req,
                                       @RequestParam(required = false) String country) {
        return service.rename(userId, req.nickname(), country);
    }

    @PostMapping("/{userId}/look/reset")
    public AdminUserDtos.Detail resetLook(@PathVariable long userId, @RequestParam(required = false) String country) {
        return service.resetLook(userId, country);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserDtos.Detail role(@AuthenticationPrincipal AuthUser actor, @PathVariable long userId, @RequestBody AdminUserDtos.RoleRequest req,
                                     @RequestParam(required = false) String country) {
        return service.setAdmin(actor.userId(), userId, req.admin(), country);
    }

    @PostMapping("/{userId}/kick")
    public ResponseEntity<Void> kick(@PathVariable long userId, @RequestParam(required = false) String country) {
        service.kick(userId, country);
        return ResponseEntity.noContent().build();
    }
}
