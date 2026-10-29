package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어 점수 시간별 기록(4.15). 플레이어당 나라 현지 시 0~23의 24칸을 덮어쓴다. 행을 지우지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_score_24h",
        uniqueConstraints = @UniqueConstraint(name = "uk_player_score_24h", columnNames = {"player_id", "hour_slot"}))
public class PlayerScore24h extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(name = "hour_slot", nullable = false, columnDefinition = "TINYINT") private int hourSlot; // 0~23
    /** 이 칸에 기록한 시간의 시작(UTC 정시). 24시간이 지난 칸은 조회에서 무시한다. */
    @Column(name = "recorded_at", nullable = false, columnDefinition = "DATETIME") private Instant recordedAt;
    @Column(name = "current_score", nullable = false) private long currentScore;

    public PlayerScore24h(Player player, int hourSlot) {
        this.player = player; this.hourSlot = hourSlot;
    }

    /** 그 시간이 끝날 때의 현재 유행도로 칸을 덮어쓴다. */
    public void overwrite(Instant recordedAt, long currentScore) {
        this.recordedAt = recordedAt;
        this.currentScore = currentScore;
    }
}
