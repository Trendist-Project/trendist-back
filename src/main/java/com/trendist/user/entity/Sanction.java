package com.trendist.user.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 제재 기록(13.5). 걸 때마다 한 행. expires_at은 만료 예정 시각(영구는 9999-12-31), released_at은 관리자가 일찍 푼 시각.
 * 제재 중인지는 released_at이 없고 expires_at이 지금보다 뒤인지로 판단한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sanctions", indexes = @Index(name = "idx_sanctions_user", columnList = "user_id, sanction_type_id, created_at"))
public class Sanction extends BaseTimeEntity {
    /** 영구 제재의 만료 시각 */
    public static final Instant FOREVER = Instant.parse("9999-12-31T23:59:59Z");

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sanction_type_id", nullable = false) private SanctionType sanctionType;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant expiresAt;
    @Column(name = "released_at", columnDefinition = "DATETIME(6)") private Instant releasedAt;

    public Sanction(User user, SanctionType type, String reason, Instant expiresAt) {
        this.user = user; this.sanctionType = type; this.reason = reason; this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) { return releasedAt == null && expiresAt.isAfter(now); }

    public void release(Instant now) { this.releasedAt = now; }
}
