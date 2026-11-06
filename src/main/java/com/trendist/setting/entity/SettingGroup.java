package com.trendist.setting.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 밸런스 설정 그룹(13.3). 관리자 설정 화면이 이 그룹별로 묶어 보여 주고, 설정(settings)이 외래 키로 그룹을 가리킨다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "setting_groups", uniqueConstraints = @UniqueConstraint(name = "uk_setting_groups_name", columnNames = "name"))
public class SettingGroup extends BaseTimeEntity {
    @Column(nullable = false, length = 50) private String name;
    @Column(name = "sort_order", nullable = false) private long sortOrder;

    public SettingGroup(String name, long sortOrder) {
        this.name = name; this.sortOrder = sortOrder;
    }

    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }
}
