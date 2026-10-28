package com.trendist.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 플레이어 = 사용자 × 나라. current_score는 실시간 점수의 DB 값(4.8). */
@Entity
@Table(name = "players",
        uniqueConstraints = @UniqueConstraint(name = "uk_players", columnNames = {"user_id", "country_id"}),
        indexes = @Index(name = "idx_players_ranking", columnList = "country_id, current_score"))
public class Player extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_id", nullable = false) private Country country;
    @Column(name = "title_id") private Long titleId;
    @Column(nullable = false, length = 40) private String nickname;
    @Column(name = "nickname_changed_at", columnDefinition = "DATETIME(6)") private Instant nicknameChangedAt;
    @Column(nullable = false) private long exp;
    @Column(nullable = false) private int level = 1;
    @Column(name = "current_score", nullable = false, precision = 12, scale = 2) private BigDecimal currentScore = BigDecimal.ZERO;
    @Column(name = "hold_count", nullable = false) private int holdCount;
    @Column(name = "total_plus_count", nullable = false) private int totalPlusCount;
    @Column(name = "comment_score", nullable = false) private int commentScore;
    @Column(name = "last_played_at", columnDefinition = "DATETIME(6)") private Instant lastPlayedAt;

    protected Player() {}

    public Player(User user, Country country, String nickname) {
        this.user = user; this.country = country; this.nickname = nickname;
    }

    public User getUser() { return user; }
    public Country getCountry() { return country; }
    public String getNickname() { return nickname; }
    public long getExp() { return exp; }
    public int getLevel() { return level; }
    public BigDecimal getCurrentScore() { return currentScore; }
    public int getHoldCount() { return holdCount; }
    public int getTotalPlusCount() { return totalPlusCount; }
    public int getCommentScore() { return commentScore; }

    public void addExp(long amount) { this.exp += amount; }
    public void setLevel(int level) { this.level = level; }
    public void setCurrentScore(BigDecimal score) { this.currentScore = score; }
    public void incTotalPlus() { this.totalPlusCount++; }
    public void incHold() { this.holdCount++; }
    public void decHold() { this.holdCount = Math.max(0, holdCount - 1); }
    public void setHoldCount(int holdCount) { this.holdCount = Math.max(0, holdCount); }
    public void touchPlayed(Instant now) { this.lastPlayedAt = now; }
}
