package com.trendist.keybinding.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 동작 종류 목록(SKILL_SLOT, SYSTEM, 4.33). 시더가 채운다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "action_types", uniqueConstraints = @UniqueConstraint(name = "uk_action_types_code", columnNames = "code"))
public class ActionType extends BaseTimeEntity {
    @Column(nullable = false, length = 20) private String code;
    @Column(nullable = false, length = 30) private String name;

    public ActionType(String code, String name) { this.code = code; this.name = name; }
    public void changeName(String name) { this.name = name; }
}
