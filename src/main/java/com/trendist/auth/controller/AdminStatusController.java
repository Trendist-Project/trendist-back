package com.trendist.auth.controller;

import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 화면과 게임의 [🛠 관리자] 버튼이 부른다: 관리자인지만(13). 권한이 없는 사람에게 도움이 될 정보(계정 번호 등)는 주지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class AdminStatusController {
    private final AccountGuard guard;

    public record AdminStatus(boolean admin) {}

    @GetMapping("/api/me/admin")
    public AdminStatus status(@AuthenticationPrincipal AuthUser user) {
        return new AdminStatus(guard.isAdmin(user.userId()));
    }
}
