package com.trendist.comment.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 댓글 투표. 한 플레이어가 댓글 하나에 한 표(LIKE / DISLIKE). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment_votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_comment_votes", columnNames = {"comment_id", "player_id"}))
public class CommentVote extends BaseTimeEntity {
    public static final String LIKE = "LIKE";
    public static final String DISLIKE = "DISLIKE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "comment_id", nullable = false) private Comment comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(nullable = false, length = 10) private String vote;

    public CommentVote(Comment comment, Player player, String vote) {
        this.comment = comment; this.player = player; this.vote = vote;
    }

    public void change(String vote) { this.vote = vote; }
}
