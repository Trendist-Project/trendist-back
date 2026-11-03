package com.trendist.game.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이어의 마지막 위치(9.2). 방 메모리에만 있던 위치를 서버를 다시 켜도 이어 가려고 30초마다·서버를 끌 때 저장한다.
 * 플레이어당 한 행이고, 저장한 시각(saved_at)이 REJOIN_KEEP_SECONDS(5분)를 넘으면 쓰지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_positions",
        uniqueConstraints = @UniqueConstraint(name = "uk_player_positions_player", columnNames = "player_id"),
        indexes = @Index(name = "idx_player_positions_saved", columnList = "saved_at"))
public class PlayerPosition extends BaseTimeEntity {
    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(nullable = false) private double x;
    @Column(nullable = false) private double y;
    @Column(name = "saved_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant savedAt;

    public PlayerPosition(long playerId, double x, double y, Instant savedAt) {
        this.playerId = playerId; this.x = x; this.y = y; this.savedAt = savedAt;
    }

    public void move(double x, double y, Instant savedAt) { this.x = x; this.y = y; this.savedAt = savedAt; }
}
