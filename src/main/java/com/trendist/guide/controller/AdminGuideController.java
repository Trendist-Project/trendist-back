package com.trendist.guide.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.guide.dto.AdminGuideDtos;
import com.trendist.guide.service.AdminGuideService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 관리자 가이드 관리(4.17, 13.4). /api/admin/**는 ROLE_ADMIN만 들어온다. */
@RestController
@RequestMapping("/api/admin/guide")
@RequiredArgsConstructor
public class AdminGuideController {
    private final AdminGuideService guides;

    @GetMapping("/categories")
    public List<AdminGuideDtos.Category> categories() { return guides.categories(); }

    @PostMapping("/categories")
    public AdminGuideDtos.Category createCategory(@RequestBody AdminGuideDtos.CategoryRequest req) { return guides.createCategory(req); }

    @PutMapping("/categories/{id}")
    public AdminGuideDtos.Category updateCategory(@PathVariable long id, @RequestBody AdminGuideDtos.CategoryRequest req) {
        return guides.updateCategory(id, req);
    }

    @PostMapping("/categories/{id}/move")
    public List<AdminGuideDtos.Category> moveCategory(@PathVariable long id, @RequestBody AdminGuideDtos.MoveRequest req) {
        return guides.moveCategory(id, req.direction());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        guides.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories/{id}/articles")
    public List<AdminGuideDtos.Article> articles(@PathVariable long id) { return guides.articles(id); }

    @PostMapping("/categories/{id}/articles")
    public AdminGuideDtos.Article createArticle(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                                @RequestBody AdminGuideDtos.ArticleRequest req) {
        return guides.createArticle(user.userId(), id, req);
    }

    @PutMapping("/articles/{id}")
    public AdminGuideDtos.Article updateArticle(@PathVariable long id, @RequestBody AdminGuideDtos.ArticleRequest req) {
        return guides.updateArticle(id, req);
    }

    @PostMapping("/articles/{id}/move")
    public List<AdminGuideDtos.Article> moveArticle(@PathVariable long id, @RequestBody AdminGuideDtos.MoveRequest req) {
        return guides.moveArticle(id, req.direction());
    }

    @DeleteMapping("/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable long id) {
        guides.deleteArticle(id);
        return ResponseEntity.noContent().build();
    }
}
