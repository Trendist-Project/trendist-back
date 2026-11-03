package com.trendist.keyword.dto;

import java.time.Instant;
import java.util.List;

/** AI 한 줄 설명 관리자 화면용(13.12). */
public final class AiCommentDtos {
    private AiCommentDtos() {}

    /** comment가 null이면 아직 설명이 없다. openRequests는 처리 전 갱신 요청 수, attempts는 AI 시도 횟수(3이면 포기한 상태) */
    public record Row(long keywordId, String text, String state, String comment, String by, Instant commentedAt,
                     long attempts, long openRequests, Instant lastRequestedAt) {}

    public record Page(List<Row> items, long total, int page, int size, boolean aiEnabled) {}

    public record EditRequest(String comment) {}
}
