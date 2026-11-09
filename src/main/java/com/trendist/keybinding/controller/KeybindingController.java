package com.trendist.keybinding.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.common.exception.ApiException;
import com.trendist.keybinding.dto.KeybindingDtos;
import com.trendist.keybinding.service.KeybindingService;
import com.trendist.player.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 내 단축키(4.33): 현재 나라 플레이어 기준. 동작마다 지금 키와 쓸 수 있는 키 목록을 함께 준다. */
@RestController
@RequestMapping("/api/me/keybindings")
@RequiredArgsConstructor
public class KeybindingController {
    private final KeybindingService service;
    private final PlayerService playerService;

    private long playerId(AuthUser user) {
        return playerService.findCurrent(user.userId()).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "플레이어를 찾을 수 없어요")).getId();
    }

    private KeybindingDtos.Response response(long playerId) {
        return new KeybindingDtos.Response(service.effective(playerId), service.keys());
    }

    @GetMapping
    public KeybindingDtos.Response get(@AuthenticationPrincipal AuthUser user) {
        return response(playerId(user));
    }

    @PutMapping
    public KeybindingDtos.Response update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody KeybindingDtos.UpdateRequest req) {
        long id = playerId(user);
        service.update(id, req.bindings());
        return response(id);
    }

    /** 전부 기본 키로 */
    @DeleteMapping
    public KeybindingDtos.Response reset(@AuthenticationPrincipal AuthUser user) {
        long id = playerId(user);
        service.reset(id, null);
        return response(id);
    }

    /** 한 동작만 기본 키로 */
    @DeleteMapping("/{action}")
    public KeybindingDtos.Response resetOne(@AuthenticationPrincipal AuthUser user, @PathVariable String action) {
        long id = playerId(user);
        service.reset(id, action);
        return response(id);
    }
}
