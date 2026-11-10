package com.trendist.monster.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 몬스터 한 번의 등장에서 플레이어마다 입힌 피해(4.35). 보상과 기록에 쓴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "monster_damages", uniqueConstraints = @UniqueConstraint(name = "uk_monster_damages", columnNames = {"spawn_id", "player_id"}))
public class MonsterDamage extends BaseTimeEntity {
    @Column(name = "spawn_id", nullable = false) private Long spawnId;
    @Column(name = "player_id", nullable = false) private Long playerId;
    @Column(nullable = false) private long damage;
    @Column(nullable = false) private int hits;

    public MonsterDamage(Long spawnId, Long playerId, long damage, int hits) {
        this.spawnId = spawnId; this.playerId = playerId; this.damage = damage; this.hits = hits;
    }
}
