package com.trendist.profile.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 내 꾸미기(4.9). */
@RestController
@RequestMapping("/api/me/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profiles;

    @GetMapping
    public ProfileDtos.Mine mine(@AuthenticationPrincipal AuthUser user) { return profiles.mine(user.userId()); }

    @PutMapping
    public ProfileDtos.Mine update(@AuthenticationPrincipal AuthUser user, @RequestBody ProfileDtos.UpdateRequest req) {
        return profiles.update(user.userId(), req);
    }
}
