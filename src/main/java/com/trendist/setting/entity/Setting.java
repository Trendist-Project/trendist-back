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
}
