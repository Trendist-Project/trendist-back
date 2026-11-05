package com.trendist.emergency.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 긴급 안내(4.29). 관리자가 한 줄을 쓰고 몇 분 동안 보일지 정하면, 그 시간 동안 모든 사용자의 게임 화면 맨 위에 흘러간다.
 * 시작은 만든 시각(createdAt), 끝은 endsAt이다. 한 번에 하나만 켜져 있어서 새로 올리면 이전 것은 취소(canceledAt)된다.
 * 기록은 남겨 두고 지우지 않는다. 모든 나라 공통이고, 영어판(messageEn)이 있으면 영어 화면(GLOBAL)에 그것을 보여 준다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "emergency_notices", indexes = @Index(name = "idx_emergency_active", columnList = "canceled_at, ends_at"))
public class EmergencyNotice extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 200) private String message;
    @Column(name = "message_en", length = 200) private String messageEn;
    @Column(name = "ends_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant endsAt;
    @Column(name = "canceled_at", columnDefinition = "DATETIME(6)") private Instant canceledAt;

    public EmergencyNotice(User author, String message, String messageEn, Instant endsAt) {
        this.user = author; this.message = message; this.messageEn = messageEn; this.endsAt = endsAt;
    }

    /** 지금 보여 주는 중인지(취소되지 않았고 끝나지 않음) */
    public boolean isActive(Instant now) { return canceledAt == null && endsAt.isAfter(now); }

    public void cancel(Instant now) { if (canceledAt == null) canceledAt = now; }
}
