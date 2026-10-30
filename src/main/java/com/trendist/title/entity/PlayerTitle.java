package com.trendist.title.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어가 받은 칭호(4.10). 한 번 받으면 잃지 않는다. created_at이 획득 시각이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_titles", uniqueConstraints = @UniqueConstraint(name = "uk_player_titles", columnNames = {"player_id", "title_id"}))
public class PlayerTitle extends BaseTimeEntity {
    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(name = "title_id", nullable = false) private long titleId;

    public PlayerTitle(long playerId, long titleId) { this.playerId = playerId; this.titleId = titleId; }
}
