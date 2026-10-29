package com.trendist.comment.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.keyword.entity.Keyword;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키워드 댓글(4.12). 삭제는 소프트 삭제이고 본문은 남긴다(관리자만 본다).
 * 추천·비추천 수는 조건부 UPDATE로만 바꿔서(9.6) updated_at은 작성·수정·삭제 때만 바뀐다. 그래서 "수정됨" 판단에 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_latest", columnList = "keyword_id, created_at, id"),
        @Index(name = "idx_comments_top", columnList = "keyword_id, deleted_at, like_count, created_at"),
        @Index(name = "idx_comments_user", columnList = "player_id")})
public class Comment extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "keyword_id", nullable = false) private Keyword keyword;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(nullable = false, length = 400) private String body;
    @Column(name = "like_count", nullable = false) private long likeCount;
    @Column(name = "dislike_count", nullable = false) private long dislikeCount;
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)") private Instant deletedAt;
    @Column(name = "report_count", nullable = false) private long reportCount;

    public Comment(Keyword keyword, Player player, String body) {
        this.keyword = keyword; this.player = player; this.body = body;
    }

    public boolean isDeleted() { return deletedAt != null; }

    /** 수정(작성 후 5분 안). 시간 검사는 서비스가 한다. */
    public void edit(String body) { this.body = body; }

    public void delete(Instant now) { this.deletedAt = now; }
}
