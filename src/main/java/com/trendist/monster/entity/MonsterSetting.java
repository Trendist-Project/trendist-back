package com.trendist.monster.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 몬스터 종류마다의 설정값(4.35). 값은 관리자 [몬스터] 탭에서 고치고, 설명·허용 범위·단위·그룹·순서는 코드(MonsterDefs)가 정한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "monster_settings", uniqueConstraints = @UniqueConstraint(name = "uk_monster_settings", columnNames = {"monster_type_id", "name"}))
public class MonsterSetting extends BaseTimeEntity {
    @Column(name = "monster_type_id", nullable = false) private Long monsterTypeId;
    @Column(nullable = false, length = 60) private String name;
    @Column(name = "value_num", nullable = false, precision = 16, scale = 4) private BigDecimal value;
    @Column(name = "min_v", nullable = false, precision = 16, scale = 4) private BigDecimal min;
    @Column(name = "max_v", nullable = false, precision = 16, scale = 4) private BigDecimal max;
    @Column(nullable = false, length = 400) private String description;
    @Column(length = 20) private String unit;
    @Column(name = "group_name", nullable = false, length = 30) private String groupName;
    @Column(name = "sort_order", nullable = false) private long sortOrder;

    public MonsterSetting(Long monsterTypeId, String name, double value, double min, double max, String description, String unit, String groupName, long sortOrder) {
        this.monsterTypeId = monsterTypeId; this.name = name; this.value = BigDecimal.valueOf(value);
        this.min = BigDecimal.valueOf(min); this.max = BigDecimal.valueOf(max);
        this.description = description; this.unit = unit; this.groupName = groupName; this.sortOrder = sortOrder;
    }

    public void changeValue(BigDecimal v) { this.value = v; }
    /** 값을 뺀 나머지는 코드가 정한다 */
    public void sync(double min, double max, String description, String unit, String groupName, long sortOrder) {
        this.min = BigDecimal.valueOf(min); this.max = BigDecimal.valueOf(max);
        this.description = description; this.unit = unit; this.groupName = groupName; this.sortOrder = sortOrder;
    }
}
