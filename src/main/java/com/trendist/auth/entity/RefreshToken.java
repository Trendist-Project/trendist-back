package com.trendist.auth.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 갱신 토큰(9.3): 계정 로그인 세션. 원본은 쿠키에만 있고 DB에는 SHA-256 해시만 둔다. 쓸 때마다 새 값으로 바꾼다(회전). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id, revoked_at"),
                @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")})
public class RefreshToken extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant expiresAt;
    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)") private Instant revokedAt;

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
