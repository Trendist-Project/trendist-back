package com.trendist.guide.service;

import com.trendist.guide.dto.AdminGuideDtos;
import java.util.List;

/** 관리자 가이드 관리(4.17, 13.4): 카테고리와 글을 만들고 고치고 지우고 순서를 바꾼다. */
public interface AdminGuideService {
    List<AdminGuideDtos.Category> categories();

    AdminGuideDtos.Category createCategory(AdminGuideDtos.CategoryRequest req);

    AdminGuideDtos.Category updateCategory(long id, AdminGuideDtos.CategoryRequest req);

    /** 한 칸 위·아래로. 전체 순서를 1부터 다시 매긴다 */
    List<AdminGuideDtos.Category> moveCategory(long id, int direction);

    /** 글이 없는 카테고리만 지운다. 글이 있으면 409(숨기기를 쓴다) */
    void deleteCategory(long id);

    List<AdminGuideDtos.Article> articles(long categoryId);

    AdminGuideDtos.Article createArticle(long authorUserId, long categoryId, AdminGuideDtos.ArticleRequest req);

    AdminGuideDtos.Article updateArticle(long id, AdminGuideDtos.ArticleRequest req);

    List<AdminGuideDtos.Article> moveArticle(long id, int direction);

    /** 소프트 삭제 */
    void deleteArticle(long id);
}
