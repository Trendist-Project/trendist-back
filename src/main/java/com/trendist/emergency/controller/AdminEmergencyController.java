package com.trendist.emergency.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.emergency.dto.EmergencyDtos;
import com.trendist.emergency.service.EmergencyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 긴급 안내(4.29, 13). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/emergency")
@RequiredArgsConstructor
public class AdminEmergencyController {
    private final EmergencyService emergency;

    @GetMapping
    public List<EmergencyDtos.Item> list() { return emergency.adminList(); }

    @PostMapping
    public EmergencyDtos.Item create(@AuthenticationPrincipal AuthUser user, @RequestBody EmergencyDtos.Request req) {
        return emergency.create(user.userId(), req);
    }

    /** 보이는 중인 안내를 지금 끝낸다 */
    @PostMapping("/{id}/cancel")
    public EmergencyDtos.Item cancel(@PathVariable long id) { return emergency.cancel(id); }
}
