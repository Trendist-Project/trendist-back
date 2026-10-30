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

    public Setting(String name, String value, String valueType, BigDecimal min, BigDecimal max, String description) {
        this.name = name; this.value = value; this.valueType = valueType;
        this.minValue = min; this.maxValue = max; this.description = description;
    }

    /** 값을 바꾼다. 허용 범위 검사는 호출하는 쪽에서 한다. */
    public void changeValue(String value) { this.value = value; }

    public void changeDescription(String description) { this.description = description; }

    /** 값 형식(INT·DECIMAL). 기본값의 형식이 바뀐 설정을 시더가 맞춘다. */
    public void changeValueType(String valueType) { this.valueType = valueType; }
}
