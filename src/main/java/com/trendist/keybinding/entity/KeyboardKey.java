package com.trendist.keybinding.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단축키로 쓸 수 있는 키 목록(4.33). code는 KeyboardEvent.code(Digit1, KeyQ, Space …)라서 한글 입력 상태에서도 같다.
 * reserved는 이미 다른 기능이 쓰는 키(이동 WASD·방향키, E, M, K, Enter, Esc, Tab 등)로, 묶을 수 없다.
 * aliasOf는 같은 키로 보는 짝(숫자패드 1 → 위쪽 숫자 1)이라 둘 중 하나를 쓰면 다른 하나도 쓴 것으로 겹침을 판단한다. 테이블 이름은 KEYS가 예약어라 keyboard_keys.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keyboard_keys", uniqueConstraints = @UniqueConstraint(name = "uk_keyboard_keys_code", columnNames = "code"))
public class KeyboardKey extends BaseTimeEntity {
    @Column(nullable = false, length = 32) private String code;
    @Column(nullable = false, length = 16) private String label;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false) private KeyCategory category;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "alias_of_id") private KeyboardKey aliasOf;
    @Column(nullable = false) private boolean reserved;
    @Column(nullable = false) private boolean enabled = true;

    public KeyboardKey(String code, String label, KeyCategory category, KeyboardKey aliasOf, boolean reserved) {
        this.code = code; this.label = label; this.category = category; this.aliasOf = aliasOf; this.reserved = reserved;
    }

    /** 시더가 코드와 맞추는 값(사용 여부는 운영에서 고치므로 건드리지 않는다) */
    public void sync(String label, KeyCategory category, KeyboardKey aliasOf, boolean reserved) {
        this.label = label; this.category = category; this.aliasOf = aliasOf; this.reserved = reserved;
    }

    /** 같은 키로 보는 짝을 모두 하나로 묶는 대표 코드(짝이 있으면 그쪽) */
    public String canonicalCode() { return aliasOf != null ? aliasOf.getCode() : code; }
}
