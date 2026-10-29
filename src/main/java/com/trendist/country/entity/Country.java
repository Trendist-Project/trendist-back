package com.trendist.country.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "countries", uniqueConstraints = @UniqueConstraint(name = "uk_countries_code", columnNames = "code"))
public class Country extends BaseTimeEntity {
    @Column(nullable = false, length = 10) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "default_locale", nullable = false, length = 10) private String defaultLocale;
    @Column(nullable = false, length = 50) private String timezone;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "max_room_size", nullable = false) private long maxRoomSize = 1000;
    @Column(name = "sort_order", nullable = false) private long sortOrder;

    public Country(String code, String name, String defaultLocale, String timezone, boolean enabled, int sortOrder) {
        this.code = code; this.name = name; this.defaultLocale = defaultLocale;
        this.timezone = timezone; this.enabled = enabled; this.sortOrder = sortOrder;
    }
}
