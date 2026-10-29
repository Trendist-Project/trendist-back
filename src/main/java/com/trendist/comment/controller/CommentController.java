package com.trendist.comment.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.comment.dto.CommentDtos;
import com.trendist.comment.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 키워드 댓글과 투표(4.12, 9.6). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService service;

    @GetMapping("/keywords/{id}/comments/top")
    public List<CommentDtos.Item> top(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return service.top(user.userId(), id);
    }

    @GetMapping("/keywords/{id}/comments")
    public CommentDtos.Page latest(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.latest(user.userId(), id, page, size);
    }

    @PostMapping("/keywords/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDtos.Item write(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                  @Valid @RequestBody CommentDtos.WriteRequest req) {
        return service.write(user.userId(), id, req.body());
    }

    @PatchMapping("/comments/{id}")
    public CommentDtos.Item edit(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                 @Valid @RequestBody CommentDtos.WriteRequest req) {
        return service.edit(user.userId(), id, req.body());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.delete(user.userId(), id);
    }

    @PostMapping("/comments/{id}/vote")
    public CommentDtos.VoteResult vote(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                       @Valid @RequestBody CommentDtos.VoteRequest req) {
        return service.vote(user.userId(), id, req.vote());
    }
}
