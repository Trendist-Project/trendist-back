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
    /**
     * 레벨 표시 옆 성장 아이콘의 바탕(4.9). badgeColor는 #RRGGBB(없으면 기본 거북이 색), badgeAlpha는 진하기(0~100%, 낮을수록 옅음),
     * badgeRainbow가 true면 색 대신 무지개 그라데이션. badgeAlpha가 null이면 아직 값을 넣지 않은 행(시더가 채운다).
     */
    @Column(name = "badge_color", length = 7) private String badgeColor;
    @Column(name = "badge_alpha") private Long badgeAlpha;
    @Column(name = "badge_rainbow", nullable = false) private boolean badgeRainbow;

    public Level(int level, long requiredExp) { this.level = level; this.requiredExp = requiredExp; }

    public void applyBadge(String color, long alpha, boolean rainbow) {
        this.badgeColor = color; this.badgeAlpha = alpha; this.badgeRainbow = rainbow;
    }
}
