package com.trendist.notice.controller;

import com.trendist.notice.dto.NoticeItem;
import com.trendist.notice.service.NoticeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공지사항(4.16). 로그인하지 않아도 볼 수 있다(메인 화면의 [공지]). */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @GetMapping
    public List<NoticeItem> list() {
        return noticeService.list();
    }
}
