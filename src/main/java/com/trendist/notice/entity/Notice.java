package com.trendist.notice.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지사항(4.16). 관리자만 쓰고, 모든 나라 공통이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notices", indexes = {
        @Index(name = "idx_notices_list", columnList = "deleted_at, pinned, created_at"),
        @Index(name = "idx_notices_popup", columnList = "deleted_at, popup_enabled, popup_until")})
public class Notice extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(nullable = false) private boolean pinned;
    @Column(name = "popup_enabled", nullable = false) private boolean popupEnabled = true;
    @Column(name = "popup_until", columnDefinition = "DATETIME(6)") private Instant popupUntil;
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)") private Instant deletedAt;

    /** 관리자 수정(4.16). 수정 시각이 바뀌어 목록에 "수정됨"이 붙는다 */
    public void change(String title, String body, boolean pinned, boolean popupEnabled, Instant popupUntil) {
        this.title = title; this.body = body; this.pinned = pinned; this.popupEnabled = popupEnabled; this.popupUntil = popupUntil;
    }

    /** 소프트 삭제(4.16) */
    public void delete(Instant now) { this.deletedAt = now; }

    public Notice(User author, String title, String body, boolean pinned, Instant popupUntil) {
        this.user = author; this.title = title; this.body = body; this.pinned = pinned; this.popupUntil = popupUntil;
    }

    public Notice(User author, String title, String body, boolean pinned, boolean popupEnabled, Instant popupUntil) {
        this(author, title, body, pinned, popupUntil);
        this.popupEnabled = popupEnabled;
    }
}
