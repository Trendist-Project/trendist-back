package com.trendist.monster.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 몬스터 등장 기록(4.35): 한 번 나올 때마다 한 줄. source는 SCHEDULE(정각)·ADMIN(관리자 즉시 소환),
 * status는 ACTIVE(싸우는 중)·KILLED(처치)·TIMEOUT(시간 초과)·REMOVED(관리자 제거)·SKIPPED(사람이 적어 건너뜀).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "monster_spawns")
public class MonsterSpawn extends BaseTimeEntity {
    @Column(name = "monster_type_id", nullable = false) private Long monsterTypeId;
    @Column(name = "country_id", nullable = false) private Long countryId;
    @Column(nullable = false, length = 20) private String source;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant startedAt;
    @Column(name = "ended_at", columnDefinition = "DATETIME(6)") private Instant endedAt;
    @Column(name = "max_hp", nullable = false) private long maxHp;
    @Column(name = "final_hp", nullable = false) private long finalHp;
    @Column(nullable = false) private int humans;
    @Column(name = "avg_score", nullable = false) private long avgScore;
    @Column(name = "top_score", nullable = false) private long topScore;
    @Column(nullable = false) private int participants;
    @Column(name = "top_player_id") private Long topPlayerId;
    @Column(name = "top_damage", nullable = false) private long topDamage;

    public MonsterSpawn(Long monsterTypeId, Long countryId, String source, String status, Instant startedAt) {
        this.monsterTypeId = monsterTypeId; this.countryId = countryId; this.source = source; this.status = status; this.startedAt = startedAt;
    }

    public void started(long maxHp, int humans, long avgScore, long topScore) {
        this.maxHp = maxHp; this.finalHp = maxHp; this.humans = humans; this.avgScore = avgScore; this.topScore = topScore;
    }

    public void finish(String status, long finalHp, int participants, Long topPlayerId, long topDamage) {
        this.status = status; this.endedAt = Instant.now(); this.finalHp = finalHp;
        this.participants = participants; this.topPlayerId = topPlayerId; this.topDamage = topDamage;
    }
}
