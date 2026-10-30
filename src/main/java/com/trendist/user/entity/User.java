package com.trendist.user.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.country.entity.Country;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_visitor_id", columnNames = "visitor_id"),
        @UniqueConstraint(name = "uk_users_oauth", columnNames = {"oauth_type", "oauth_id"})})
public class User extends BaseTimeEntity {
    public static final String ROLE_ADMIN = "ADMIN";

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "country_id") private Country country;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_state_id", nullable = false) private UserState userState;
    @Column(name = "visitor_id", length = 51) private String visitorId;
    @Column(name = "oauth_type", length = 20) private String oauthType;
    @Column(name = "oauth_id", length = 64) private String oauthId;
    @Column(name = "last_login_at", columnDefinition = "DATETIME(6)") private Instant lastLoginAt;
    @Column(nullable = false, length = 20) private String role = "USER";
    @Column(name = "ui_locale", nullable = false, length = 10) private String uiLocale = "ko";
    @Column(name = "banned_until", columnDefinition = "DATETIME(6)") private Instant bannedUntil;
    @Column(name = "upload_banned_until", columnDefinition = "DATETIME(6)") private Instant uploadBannedUntil;
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)") private Instant deletedAt;
    /** 로그아웃한 시각. 이 시각 이전에 발급한 액세스 토큰(JWT)은 남은 수명과 관계없이 거절한다(9.3). */
    @Column(name = "tokens_revoked_at", columnDefinition = "DATETIME(6)") private Instant tokensRevokedAt;

    public static User guest(String visitorId, UserState state) {
        User u = new User();
        u.visitorId = visitorId;
        u.userState = state;
        return u;
    }

    /**
     * 운영자 계정. 공지·가이드의 작성자(notices.user_id, guides.user_id)로 쓴다.
     * 방문자 ID도 OAuth도 없어서 로그인할 수 없고, 관리자 로그인은 관리자 기능과 함께 붙인다.
     */
    public static User operator(UserState state) {
        User u = new User();
        u.userState = state;
        u.role = ROLE_ADMIN;
        return u;
    }

    public static final String OAUTH_GOOGLE = "GOOGLE";

    /** 구글로 처음 로그인한 새 계정: OAuth 종류와 ID(구글 sub의 SHA-256 해시)만 둔다. 이름·이메일·사진은 쓰지 않는다(4.1). */
    public static User google(String oauthId, UserState state) {
        User u = new User();
        u.oauthType = OAUTH_GOOGLE;
        u.oauthId = oauthId;
        u.userState = state;
        return u;
    }

    /** 게스트로 플레이하던 계정에 구글을 연결한다. OAuth 값만 채우고 나머지(플레이어·기록)는 그대로라 기록이 이어진다. */
    public void linkGoogle(String oauthId) {
        this.oauthType = OAUTH_GOOGLE;
        this.oauthId = oauthId;
    }

    public boolean isGuest() { return oauthId == null; }

    public void touchLogin(Instant now) { this.lastLoginAt = now; }

    /** 로그아웃: 지금까지 발급한 액세스 토큰을 모두 무효로 한다. */
    public void revokeTokens(Instant now) { this.tokensRevokedAt = now; }

    /** 탈퇴(4.1): 저장된 값은 그대로 두고 상태와 시각만 바꾼다. 지금까지 발급한 액세스 토큰도 무효로 한다. */
    public void withdraw(UserState withdrawn, Instant now) {
        this.userState = withdrawn;
        this.deletedAt = now;
        this.tokensRevokedAt = now;
    }

    /** 복구(4.1): 같은 구글 계정으로 다시 로그인해 복구를 고르면 정상 상태로 되돌린다. 기록은 탈퇴 전 그대로다. */
    public void restore(UserState normal) {
        this.userState = normal;
        this.deletedAt = null;
    }

    public boolean isWithdrawn() { return deletedAt != null; }

    public boolean isBanned(Instant now) { return bannedUntil != null && bannedUntil.isAfter(now); }
    public void setCountry(Country country) { this.country = country; }
}
