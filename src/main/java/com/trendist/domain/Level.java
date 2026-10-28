package com.trendist.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "levels", uniqueConstraints = @UniqueConstraint(name = "uk_levels_level", columnNames = "level"))
public class Level extends BaseTimeEntity {
    @Column(nullable = false) private int level;
    @Column(name = "required_exp", nullable = false) private long requiredExp;

    protected Level() {}
    public Level(int level, long requiredExp) { this.level = level; this.requiredExp = requiredExp; }

    public int getLevel() { return level; }
    public long getRequiredExp() { return requiredExp; }
}
