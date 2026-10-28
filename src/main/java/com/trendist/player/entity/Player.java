package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.country.entity.Country;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어 = 사용자 × 나라. current_score는 실시간 점수의 DB 값(4.8). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public Player(User user, Country country, String nickname) {
        this.user = user; this.country = country; this.nickname = nickname;
    }

    public void addExp(long amount) { this.exp += amount; }
    public void setLevel(int level) { this.level = level; }
    public void setCurrentScore(BigDecimal score) { this.currentScore = score; }
    public void incTotalPlus() { this.totalPlusCount++; }
    public void incHold() { this.holdCount++; }
    public void decHold() { this.holdCount = Math.max(0, holdCount - 1); }
    public void setHoldCount(int holdCount) { this.holdCount = Math.max(0, holdCount); }
    public void touchPlayed(Instant now) { this.lastPlayedAt = now; }

    /** 닉네임 변경. 변경 간격 검사는 서비스가 하고, 여기서는 변경 시각을 함께 남긴다(4.1). */
    public void changeNickname(String nickname, Instant now) {
        this.nickname = nickname;
        this.nicknameChangedAt = now;
    }
}
