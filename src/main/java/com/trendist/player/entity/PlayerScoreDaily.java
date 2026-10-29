package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어 점수 하루별 기록(4.15). 나라 현지 날짜 하루에 한 행, 그날 마지막 값. 영구 보관. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_score_dailies",
        uniqueConstraints = @UniqueConstraint(name = "uk_player_score_dailies", columnNames = {"player_id", "record_date"}))
public class PlayerScoreDaily extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(name = "record_date", nullable = false) private LocalDate recordDate;
    @Column(name = "current_score", nullable = false) private long currentScore;

    public PlayerScoreDaily(Player player, LocalDate recordDate) {
        this.player = player; this.recordDate = recordDate;
    }

    public void overwrite(long currentScore) { this.currentScore = currentScore; }
}
