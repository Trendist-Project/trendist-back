package com.trendist.skill.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.keybinding.entity.GameAction;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이어가 장착한 스킬(4.33). 칸은 actions 중 종류가 SKILL_SLOT인 동작이라 칸 수와 단축키 칸이 한 목록을 따른다.
 * skill이 null이면 그 칸을 일부러 비운 것이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_skill_slots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_skill_slots_action", columnNames = {"player_id", "action_id"}),
        @UniqueConstraint(name = "uk_player_skill_slots_skill", columnNames = {"player_id", "skill_id"})})
public class PlayerSkillSlot extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "action_id", nullable = false) private GameAction action;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "skill_id") private Skill skill;

    public PlayerSkillSlot(Player player, GameAction action, Skill skill) {
        this.player = player; this.action = action; this.skill = skill;
    }
}
