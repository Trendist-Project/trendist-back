package com.trendist.share.controller;

import com.trendist.share.dto.ShareDtos;
import com.trendist.share.service.ShareCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 공유 통계(13.8). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/share-cards")
@RequiredArgsConstructor
public class AdminShareController {
    private final ShareCardService service;

    @GetMapping
    public ShareDtos.AdminStats list(@RequestParam(defaultValue = "views") String sort, @RequestParam(defaultValue = "0") int page) {
        return service.adminStats(sort, page);
    }
}
