package com.trendist.keybinding.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키를 묶을 수 있는 동작(4.33). 코드는 skill_slot_1~4, dash 같은 값이고 종류가 SKILL_SLOT이면 param이 칸 번호다.
 * defaultKey는 사용자가 바꾸지 않았을 때 쓰는 기본 키라서, key_bindings에는 바꾼 것만 저장한다. 새 기능은 이 표에 행을 더하면 키를 묶을 수 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "actions", uniqueConstraints = @UniqueConstraint(name = "uk_actions_code", columnNames = "code"))
public class GameAction extends BaseTimeEntity {
    @Column(nullable = false, length = 40) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "type_id", nullable = false) private ActionType type;
    private Integer param;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "default_key_id") private KeyboardKey defaultKey;
    @Column(name = "sort_order", nullable = false) private long sortOrder;

    public GameAction(String code, ActionType type, Integer param, KeyboardKey defaultKey, long sortOrder) {
        this.code = code; this.type = type; this.param = param; this.defaultKey = defaultKey; this.sortOrder = sortOrder;
    }
    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }
    public void changeType(ActionType type, Integer param) { this.type = type; this.param = param; }
}
