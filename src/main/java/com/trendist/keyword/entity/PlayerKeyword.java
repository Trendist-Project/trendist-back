package com.trendist.keyword.entity;

import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내 트렌드 키워드. + 한 번 = 한 행, 24시간 뒤 삭제. 만료 행을 재사용하면 created_at(+ 시각)을 새로 쓴다(9.4). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_keywords",
        uniqueConstraints = @UniqueConstraint(name = "uk_player_keywords", columnNames = {"player_id", "keyword_id"}),
        indexes = {
                @Index(name = "idx_player_keywords_keyword", columnList = "keyword_id"),
                @Index(name = "idx_player_keywords_expires", columnList = "expires_at")})
public class PlayerKeyword {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "keyword_id", nullable = false) private Keyword keyword;
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant expiresAt;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant createdAt;
    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant updatedAt;

    public PlayerKeyword(Player player, Keyword keyword, Instant now, Instant expiresAt) {
        this.player = player; this.keyword = keyword; this.createdAt = now; this.expiresAt = expiresAt;
    }

    public void renew(Instant now, Instant expiresAt) { this.createdAt = now; this.expiresAt = expiresAt; }

    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
}
