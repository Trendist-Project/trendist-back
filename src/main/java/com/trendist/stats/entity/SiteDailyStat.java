package com.trendist.stats.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사이트 통계 하루별 기록(13.9). 나라 × 현지 날짜 한 행. 매시간 그날의 시간별 행을 합쳐 다시 쓴다.
 * 활동 인원(DAU)은 그날 한 번이라도 방에 있던 사람 수라 시간별 합이 아니라 따로 센다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "site_daily_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_site_daily_stats", columnNames = {"country_id", "record_date"}))
public class SiteDailyStat extends BaseTimeEntity implements StatCounts {
    @Column(name = "country_id", nullable = false) private long countryId;
    @Column(name = "record_date", nullable = false) private LocalDate recordDate;
    @Column(name = "peak_online", nullable = false) private long peakOnline;
    @Column(name = "active_players", nullable = false) private long activePlayers;
    @Column(name = "new_players", nullable = false) private long newPlayers;
    @Column(name = "plus_count", nullable = false) private long plusCount;
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "orb_count", nullable = false) private long orbCount;
    @Column(name = "collision_count", nullable = false) private long collisionCount;
    @Column(name = "chat_count", nullable = false) private long chatCount;
    @Column(name = "comment_count", nullable = false) private long commentCount;
    @Column(name = "share_count", nullable = false) private long shareCount;

    public SiteDailyStat(long countryId, LocalDate recordDate) {
        this.countryId = countryId;
        this.recordDate = recordDate;
    }

    /** 그날의 시간별 합으로 다시 쓴다. 활동 인원은 줄지 않게 큰 값을 남긴다(서버를 다시 켜면 메모리의 그날 명단이 비므로). */
    public void rewrite(long peakOnline, long activePlayers, long[] c) {
        this.peakOnline = peakOnline;
        this.activePlayers = Math.max(this.activePlayers, activePlayers);
        this.newPlayers = c[0]; this.plusCount = c[1]; this.searchCount = c[2]; this.orbCount = c[3];
        this.collisionCount = c[4]; this.chatCount = c[5]; this.commentCount = c[6]; this.shareCount = c[7];
    }
}
