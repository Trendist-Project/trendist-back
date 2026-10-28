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

    public boolean isBanned(Instant now) { return bannedUntil != null && bannedUntil.isAfter(now); }
    public void setCountry(Country country) { this.country = country; }
}
