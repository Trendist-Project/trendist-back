package com.trendist.title.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 칭호. 조건은 condition_type(METRIC/RANK) + condition_params(JSON) 데이터로 운영한다(4.10). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "titles", uniqueConstraints = @UniqueConstraint(name = "uk_titles_name", columnNames = "name"))
public class Title extends BaseTimeEntity {
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Column(length = 100) private String icon;
    @Column(name = "condition_type", nullable = false, length = 20) private String conditionType;
    @Column(name = "condition_params", nullable = false, columnDefinition = "JSON") private String conditionParams;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    /** 표시 순서. 시드가 아직 처음 값 그대로인 행만 새 순서로 바꿀 때 쓴다 */
    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }

    public Title(String name, String description, String conditionType, String conditionParams, int sortOrder) {
        this.name = name; this.description = description; this.conditionType = conditionType;
        this.conditionParams = conditionParams; this.sortOrder = sortOrder;
    }
}
