package com.trendist.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_visitor_id", columnNames = "visitor_id"),
        @UniqueConstraint(name = "uk_users_oauth", columnNames = {"oauth_type", "oauth_id"})})
public class User extends BaseTimeEntity {
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

    protected User() {}

    public static User guest(String visitorId, UserState state) {
        User u = new User();
        u.visitorId = visitorId;
        u.userState = state;
        return u;
    }

    public boolean isBanned(Instant now) { return bannedUntil != null && bannedUntil.isAfter(now); }
    public UserState getUserState() { return userState; }
    public String getVisitorId() { return visitorId; }
    public String getOauthId() { return oauthId; }
    public String getRole() { return role; }
    public Country getCountry() { return country; }
    public void setCountry(Country country) { this.country = country; }
}
