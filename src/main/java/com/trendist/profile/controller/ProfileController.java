package com.trendist.profile.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.common.support.Lang;
import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.service.ProfileService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 내 꾸미기 프로필(4.9)과 픽셀 그림 이미지. */
@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profiles;

    @GetMapping("/api/me/profiles")
    public ProfileDtos.Box box(@AuthenticationPrincipal AuthUser user,
                               @RequestHeader(value = "Accept-Language", required = false) String lang) {
        return profiles.box(user.userId(), Lang.parse(lang));
    }

    @PutMapping("/api/me/profiles/{slot}")
    public ProfileDtos.Box save(@AuthenticationPrincipal AuthUser user, @PathVariable int slot, @RequestBody ProfileDtos.SaveRequest req,
                                   @RequestHeader(value = "Accept-Language", required = false) String lang) {
        return profiles.save(user.userId(), slot, req, Lang.parse(lang));
    }

    @PutMapping("/api/me/profiles/equipped")
    public ProfileDtos.Box equip(@AuthenticationPrincipal AuthUser user, @RequestBody ProfileDtos.EquipRequest req,
                                    @RequestHeader(value = "Accept-Language", required = false) String lang) {
        return profiles.equip(user.userId(), req.slot(), Lang.parse(lang));
    }

    /** 픽셀 그림 PNG(로그인 없음). 주소에 바꾼 시각(?v=)이 붙어 같은 주소의 그림은 바뀌지 않으므로 오래 캐시해도 된다. */
    @GetMapping("/api/art/{id}.png")
    public ResponseEntity<byte[]> art(@PathVariable long id) {
        return profiles.artPng(id)
                .map(png -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).body(png))
                .orElse(ResponseEntity.notFound().build());
    }
}
