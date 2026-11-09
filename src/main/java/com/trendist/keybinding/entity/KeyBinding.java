package com.trendist.keybinding.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이어가 바꾼 키 연결(4.33). 플레이어마다 동작 하나에 한 줄이고(player_id + action_id 유일), 한 키는 한 동작에만 쓴다(player_id + key_id 유일).
 * key가 null이면 일부러 비운 상태다(행이 없으면 기본 키). 기본 키와 같아지는 설정은 행을 두지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "key_bindings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_key_bindings_player_action", columnNames = {"player_id", "action_id"}),
        @UniqueConstraint(name = "uk_key_bindings_player_key", columnNames = {"player_id", "key_id"})})
public class KeyBinding extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "action_id", nullable = false) private GameAction action;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "key_id") private KeyboardKey key;

    public KeyBinding(Player player, GameAction action, KeyboardKey key) { this.player = player; this.action = action; this.key = key; }
}
