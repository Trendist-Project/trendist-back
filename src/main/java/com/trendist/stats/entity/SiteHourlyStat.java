package com.trendist.stats.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사이트 통계 시간별 기록(13.9). 나라 × 한 시간(recorded_at = 그 시간의 시작, UTC 정시) 한 행.
 * 접속 인원은 10초마다 센 방 인원의 최고·평균, 활동 인원은 그 시간에 한 번이라도 방에 있던 사람 수다(봇 제외).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "site_hourly_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_site_hourly_stats", columnNames = {"country_id", "recorded_at"}))
public class SiteHourlyStat extends BaseTimeEntity implements StatCounts {
    @Column(name = "country_id", nullable = false) private long countryId;
    @Column(name = "recorded_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant recordedAt;
    @Column(name = "peak_online", nullable = false) private long peakOnline;
    @Column(name = "avg_online", nullable = false, columnDefinition = "DECIMAL(10,2)") private double avgOnline;
    @Column(name = "active_players", nullable = false) private long activePlayers;
    @Column(name = "new_players", nullable = false) private long newPlayers;
    @Column(name = "plus_count", nullable = false) private long plusCount;
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "orb_count", nullable = false) private long orbCount;
    @Column(name = "collision_count", nullable = false) private long collisionCount;
    @Column(name = "chat_count", nullable = false) private long chatCount;
    @Column(name = "comment_count", nullable = false) private long commentCount;
    @Column(name = "share_count", nullable = false) private long shareCount;

    public SiteHourlyStat(long countryId, Instant recordedAt) {
        this.countryId = countryId;
        this.recordedAt = recordedAt;
    }

    /** 그 시간의 값을 덮어쓴다(같은 시간을 다시 저장하면 값이 더해진다: 재시작 전후로 나뉜 경우). */
    public void merge(long peakOnline, double avgOnline, long activePlayers, long[] counts) {
        boolean first = this.peakOnline == 0 && this.activePlayers == 0;
        this.avgOnline = first ? avgOnline : Math.max(this.avgOnline, avgOnline);
        this.peakOnline = Math.max(this.peakOnline, peakOnline);
        this.activePlayers = Math.max(this.activePlayers, activePlayers);
        this.newPlayers += counts[0]; this.plusCount += counts[1]; this.searchCount += counts[2]; this.orbCount += counts[3];
        this.collisionCount += counts[4]; this.chatCount += counts[5]; this.commentCount += counts[6]; this.shareCount += counts[7];
    }
}
