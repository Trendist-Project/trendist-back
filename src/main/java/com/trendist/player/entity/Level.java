package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/** 레벨표(4.9). players.level_id가 이 행을 가리킨다. 플레이어 여러 명을 읽을 때 레벨 행을 한 번에 모아 읽는다(BatchSize). */
@Getter
@BatchSize(size = 100)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "levels", uniqueConstraints = @UniqueConstraint(name = "uk_levels_level", columnNames = "level"))
public class Level extends BaseTimeEntity {
    @Column(nullable = false) private long level;
    @Column(name = "required_exp", nullable = false) private long requiredExp;

    public Level(int level, long requiredExp) { this.level = level; this.requiredExp = requiredExp; }
}
