package com.trendist.emergency.controller;

import com.trendist.emergency.dto.EmergencyDtos;
import com.trendist.emergency.service.EmergencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 지금 보여 줄 긴급 안내(4.29). 로그인하지 않아도 읽는다. 게임 화면이 열릴 때와 주기적으로 부른다. */
@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
public class EmergencyController {
    private final EmergencyService emergency;

    @GetMapping
    public EmergencyDtos.Current current() { return new EmergencyDtos.Current(emergency.active().orElse(null)); }
}
