package com.trendist.guide.dto;

import java.time.Instant;

/** 관리자 가이드 관리(4.17, 13.4). */
public final class AdminGuideDtos {
    private AdminGuideDtos() {}

    /** code는 만들 때만 쓴다(소문자·숫자·하이픈 2~50자, 바꿀 수 없다). displayType: FAQ 또는 ARTICLE */
    public record CategoryRequest(String code, String name, String displayType, Boolean enabled) {}

    /** articleCount는 게시 중인 글 수 */
    public record Category(long id, String code, String name, String displayType, boolean enabled, long sortOrder, int articleCount) {}

    public record ArticleRequest(String title, String body) {}

    public record Article(long id, long categoryId, String title, String body, long sortOrder, Instant createdAt, Instant updatedAt) {}

    /** direction: -1이면 위로, 1이면 아래로 한 칸 */
    public record MoveRequest(int direction) {}
}
