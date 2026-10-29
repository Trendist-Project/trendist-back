package com.trendist.comment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class CommentDtos {
    private CommentDtos() {}

    /**
     * 댓글 한 개(4.12). 삭제·탈퇴 댓글은 body를 안내 문구로 바꿔 보낸다(원문은 DB에만 둔다).
     * myVote: LIKE / DISLIKE / null. editable: 내 댓글이고 작성 5분 안.
     */
    public record Item(long id, String nickname, String body, int likeCount, int dislikeCount, String myVote,
                       boolean mine, boolean editable, boolean deleted, boolean withdrawn, boolean edited, Instant createdAt) {}

    /** 최신순 한 페이지(0부터). total은 이 키워드의 전체 댓글 수(삭제 포함). */
    public record Page(List<Item> items, int page, int totalPages, long total) {}

    public record WriteRequest(@NotNull String body) {}

    public record VoteRequest(@NotNull String vote) {}

    public record VoteResult(String myVote, int likeCount, int dislikeCount) {}
}
