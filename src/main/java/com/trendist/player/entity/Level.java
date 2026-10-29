package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "levels", uniqueConstraints = @UniqueConstraint(name = "uk_levels_level", columnNames = "level"))
public class Level extends BaseTimeEntity {
    @Column(nullable = false) private long level;
    @Column(name = "required_exp", nullable = false) private long requiredExp;

    public Level(int level, long requiredExp) { this.level = level; this.requiredExp = requiredExp; }
}
