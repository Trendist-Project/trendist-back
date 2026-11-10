package com.trendist.audit.controller;

import com.trendist.audit.service.ConfigRollbackService;
import com.trendist.auth.security.AuthUser;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 설정 변경 이력(13.17). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/config-changes")
@RequiredArgsConstructor
public class AdminConfigChangeController {
    private final ConfigRollbackService service;

    public record MemoRequest(String memo) {}

    /** 최신순. scope(SETTING·MONSTER·SKILL)·q(대상 이름에 포함)·beforeId(이 번호보다 앞선 것만, 더 보기)는 선택 */
    @GetMapping
    public List<ConfigRollbackService.Item> list(@RequestParam(defaultValue = "") String scope, @RequestParam(defaultValue = "") String q,
                                                 @RequestParam(defaultValue = "0") long beforeId, @RequestParam(defaultValue = "50") int limit) {
        return service.list(scope, q, beforeId, limit);
    }

    @PostMapping("/{id}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.rollback(id, user.userId());
        return Map.of("ok", true);
    }

    @PatchMapping("/{id}/memo")
    public Map<String, Object> memo(@PathVariable long id, @RequestBody MemoRequest req) {
        service.memo(id, req.memo());
        return Map.of("ok", true);
    }
}
