package com.trendist.guide.dto;

import java.time.Instant;

public final class GuideDtos {
    private GuideDtos() {}

    /** displayType: FAQ(아코디언) 또는 ARTICLE(문서형). */
    public record Category(String code, String name, String displayType) {}

    public record Article(long id, String title, String body, Instant updatedAt) {}
}
