package com.trendist.wheel.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.wheel.dto.WheelDtos;
import com.trendist.wheel.service.WheelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 하루 한 번 돌림판. */
@RestController
@RequestMapping("/api/me/wheel")
@RequiredArgsConstructor
public class WheelController {
    private final WheelService wheel;

    @GetMapping
    public WheelDtos.Status status(@AuthenticationPrincipal AuthUser user) { return wheel.status(user.userId()); }

    @PostMapping("/spin")
    public WheelDtos.SpinResult spin(@AuthenticationPrincipal AuthUser user) { return wheel.spin(user.userId()); }
}
