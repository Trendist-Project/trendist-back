package com.trendist.title.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.title.dto.TitleDtos;
import com.trendist.title.service.TitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 칭호함과 활성 칭호(4.10). */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class TitleController {
    private final TitleService titles;

    @GetMapping("/titles")
    public TitleDtos.Box box(@AuthenticationPrincipal AuthUser user) { return titles.box(user.userId()); }

    @PutMapping("/title")
    public TitleDtos.Box activate(@AuthenticationPrincipal AuthUser user, @RequestBody TitleDtos.ActivateRequest req) {
        return titles.activate(user.userId(), req.titleId());
    }
}
