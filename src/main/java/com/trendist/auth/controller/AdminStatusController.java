package com.trendist.auth.controller;

import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 화면이 처음에 부른다: 내 계정 번호와 관리자인지. 관리자가 아니면 화면에 계정 번호를 보여 줘서
 * 운영자가 DB에서 권한(users.role = ADMIN)을 줄 수 있게 한다(13.7).
 */
@RestController
@RequiredArgsConstructor
public class AdminStatusController {
    private final AccountGuard guard;

    public record AdminStatus(long userId, boolean admin) {}

    @GetMapping("/api/me/admin")
    public AdminStatus status(@AuthenticationPrincipal AuthUser user) {
        return new AdminStatus(user.userId(), guard.isAdmin(user.userId()));
    }
}
