package com.trendist.setting.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "settings", uniqueConstraints = @UniqueConstraint(name = "uk_settings_name", columnNames = "name"))
public class Setting extends BaseTimeEntity {
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 2000) private String value;
    @Column(name = "value_type", nullable = false, length = 20) private String valueType;
    @Column(name = "min_value", precision = 18, scale = 6) private BigDecimal minValue;
    @Column(name = "max_value", precision = 18, scale = 6) private BigDecimal maxValue;
    @Column(length = 500) private String description;
    /** 값 뒤에 붙여 보여 줄 단위(초, px 등). 단위가 없는 값은 null */
    @Column(length = 20) private String unit;
    /** 관리자 화면에 보이는 순서(작을수록 위, 전체에서 이어지는 번호). 그룹 안의 순서는 이 값으로 정해진다. 코드(SettingKeys)가 정하고 시더가 맞춘다 */
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_settings_group"))
    private SettingGroup group;

    public Setting(String name, String value, String valueType, BigDecimal min, BigDecimal max, String description,
                   String unit, SettingGroup group, long sortOrder) {
        this.name = name; this.value = value; this.valueType = valueType;
        this.minValue = min; this.maxValue = max; this.description = description;
        this.unit = unit; this.group = group; this.sortOrder = sortOrder;
    }

    /** 값을 바꾼다. 허용 범위 검사는 호출하는 쪽에서 한다. */
    public void changeValue(String value) { this.value = value; }

    public void changeDescription(String description) { this.description = description; }

    /** 단위와 그룹은 코드(SettingKeys)가 정한다. 시더가 맞춘다. */
    public void changeUnit(String unit) { this.unit = unit; }

    public void changeGroup(SettingGroup group) { this.group = group; }

    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }

    /** 값 형식(INT·DECIMAL). 기본값의 형식이 바뀐 설정을 시더가 맞춘다. */
    public void changeValueType(String valueType) { this.valueType = valueType; }
}
