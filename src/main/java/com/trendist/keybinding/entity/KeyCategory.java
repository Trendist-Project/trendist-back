package com.trendist.keybinding.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 키 분류 목록(DIGIT·LETTER·FUNCTION·NUMPAD·SPECIAL, 4.33). 시더가 채운다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "key_categories", uniqueConstraints = @UniqueConstraint(name = "uk_key_categories_code", columnNames = "code"))
public class KeyCategory extends BaseTimeEntity {
    @Column(nullable = false, length = 20) private String code;
    @Column(nullable = false, length = 30) private String name;

    public KeyCategory(String code, String name) { this.code = code; this.name = name; }
    public void changeName(String name) { this.name = name; }
}
