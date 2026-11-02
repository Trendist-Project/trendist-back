package com.trendist.effect.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원을 꾸미는 효과 하나(4.9 테두리 효과, 4.23 이동 효과). 종류(effect_types)를 가리키고, 꾸미기 프로필(profiles)이 종류마다 하나씩 고른다.
 * 그림은 프론트엔드 코드가 code로 그리고, 이름·얻는 조건·순서·사용 여부는 이 데이터로 운영한다.
 * 효과는 꾸미기의 하나라 꾸미기 해금 레벨(기본 11)부터 Google 로그인 사용자만 쓰고, 그 위에 얻는 조건을 더 붙일 수 있다:
 * FREE(조건 없음) / LEVEL(unlock_value 레벨 이상) / TITLE(unlock_value 칭호 보유) / PURCHASE(결제, 아직 없음).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "effects", uniqueConstraints = @UniqueConstraint(name = "uk_effects", columnNames = {"effect_type_id", "code"}))
public class Effect extends BaseTimeEntity {
    public static final String FREE = "FREE", LEVEL = "LEVEL", TITLE = "TITLE", PURCHASE = "PURCHASE";

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "effect_type_id", nullable = false) private EffectType effectType;
    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "unlock_type", nullable = false, length = 20) private String unlockType = FREE;
    @Column(name = "unlock_value") private Long unlockValue;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    public Effect(EffectType effectType, String code, String name, int sortOrder) {
        this.effectType = effectType; this.code = code; this.name = name; this.sortOrder = sortOrder;
    }

    public boolean isType(String typeCode) { return effectType.getCode().equals(typeCode); }
}
