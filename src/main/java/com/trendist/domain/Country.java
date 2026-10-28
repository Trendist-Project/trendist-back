package com.trendist.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "countries", uniqueConstraints = @UniqueConstraint(name = "uk_countries_code", columnNames = "code"))
public class Country extends BaseTimeEntity {
    @Column(nullable = false, length = 10) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "default_locale", nullable = false, length = 10) private String defaultLocale;
    @Column(nullable = false, length = 50) private String timezone;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "max_room_size", nullable = false) private int maxRoomSize = 1000;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected Country() {}

    public Country(String code, String name, String defaultLocale, String timezone, boolean enabled, int sortOrder) {
        this.code = code; this.name = name; this.defaultLocale = defaultLocale;
        this.timezone = timezone; this.enabled = enabled; this.sortOrder = sortOrder;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
    public boolean isEnabled() { return enabled; }
    public int getMaxRoomSize() { return maxRoomSize; }
}
