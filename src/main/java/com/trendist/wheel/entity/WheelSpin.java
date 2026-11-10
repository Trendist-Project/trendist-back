package com.trendist.wheel.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 돌림판 한 번(하루에 한 번): 그날(플레이어 나라 시간대) 받은 활성 키워드 개수를 남긴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wheel_spins", uniqueConstraints = @UniqueConstraint(name = "uk_wheel_spins_player_date", columnNames = {"player_id", "spin_date"}))
public class WheelSpin extends BaseTimeEntity {
    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(name = "spin_date", nullable = false) private LocalDate spinDate;
    @Column(name = "reward_count", nullable = false) private int rewardCount;

    public WheelSpin(long playerId, LocalDate spinDate, int rewardCount) { this.playerId = playerId; this.spinDate = spinDate; this.rewardCount = rewardCount; }
}
