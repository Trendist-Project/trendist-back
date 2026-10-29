package com.trendist.report.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고(13.5). 대상은 유형 + ID이고 대상 테이블이 여러 개라 외래 키가 없다. 한 플레이어는 같은 대상을 한 번만 신고한다.
 * 모든 신고는 관리자가 검토한다(RECEIVED → RESOLVED / REJECTED). 신고 수에 따른 자동 조치는 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_reports_once", columnNames = {"report_type_id", "target_id", "player_id"}),
        indexes = @Index(name = "idx_reports_status", columnList = "status, created_at"))
public class Report extends BaseTimeEntity {
    public static final String RECEIVED = "RECEIVED";
    public static final String RESOLVED = "RESOLVED"; // 조치함
    public static final String REJECTED = "REJECTED"; // 문제없음
    public static final java.util.Set<String> STATUSES = java.util.Set.of(RECEIVED, RESOLVED, REJECTED);

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_type_id", nullable = false) private ReportType reportType;
    @Column(name = "target_id", nullable = false) private long targetId;
    @Column(length = 500) private String reason;
    @Column(nullable = false, length = 20) private String status = RECEIVED;

    public Report(Player player, ReportType reportType, long targetId, String reason) {
        this.player = player; this.reportType = reportType; this.targetId = targetId; this.reason = reason;
    }

    /** 관리자 검토 결과(RECEIVED / RESOLVED / REJECTED). */
    public void changeStatus(String status) { this.status = status; }
}
