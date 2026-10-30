package com.trendist.notice.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.notice.dto.AdminNoticeDtos;
import com.trendist.notice.service.NoticeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 공지 작성·수정·삭제(4.16). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {
    private final NoticeService notices;

    @GetMapping
    public List<AdminNoticeDtos.Item> list() { return notices.adminList(); }

    @PostMapping
    public AdminNoticeDtos.Item create(@AuthenticationPrincipal AuthUser user, @RequestBody AdminNoticeDtos.Request req) {
        return notices.create(user.userId(), req);
    }

    @PutMapping("/{id}")
    public AdminNoticeDtos.Item update(@PathVariable long id, @RequestBody AdminNoticeDtos.Request req) { return notices.update(id, req); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        notices.delete(id);
        return ResponseEntity.noContent().build();
    }
}
