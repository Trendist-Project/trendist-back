package com.trendist.effect.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.effect.dto.EffectDtos;
import com.trendist.effect.service.EffectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 이동 효과 고르기(4.23). */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class EffectController {
    private final EffectService effects;

    @GetMapping("/effects")
    public EffectDtos.Box box(@AuthenticationPrincipal AuthUser user) { return effects.box(user.userId()); }

    @PutMapping("/effect")
    public EffectDtos.Box choose(@AuthenticationPrincipal AuthUser user, @RequestBody EffectDtos.ChooseRequest req) {
        return effects.choose(user.userId(), req.effectId());
    }
}
