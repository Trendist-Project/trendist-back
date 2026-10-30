package com.trendist.effect.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이동 효과(4.23). 그림은 프론트엔드 코드가 code로 그리고, 이름·설명·얻는 조건·순서·사용 여부는 이 데이터로 운영한다.
 * 얻는 조건: FREE(누구나) / LEVEL(unlock_value 레벨 이상) / TITLE(unlock_value 칭호 보유) / PURCHASE(결제, 아직 없음).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trail_effects", uniqueConstraints = @UniqueConstraint(name = "uk_trail_effects_code", columnNames = "code"))
public class TrailEffect extends BaseTimeEntity {
    public static final String FREE = "FREE", LEVEL = "LEVEL", TITLE = "TITLE", PURCHASE = "PURCHASE";

    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Column(name = "unlock_type", nullable = false, length = 20) private String unlockType = FREE;
    @Column(name = "unlock_value") private Long unlockValue;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    public TrailEffect(String code, String name, String description, int sortOrder) {
        this.code = code; this.name = name; this.description = description; this.sortOrder = sortOrder;
    }
}
