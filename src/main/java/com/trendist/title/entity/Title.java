package com.trendist.title.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.common.support.Lang;
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
    @Column(name = "name_en", length = 100) private String nameEn;
    @Column(name = "description_en", length = 500) private String descriptionEn;
    @Column(length = 100) private String icon;
    @Column(name = "condition_type", nullable = false, length = 20) private String conditionType;
    @Column(name = "condition_params", nullable = false, columnDefinition = "JSON") private String conditionParams;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    /** 그 언어의 이름·설명. 영어 값이 비어 있으면 한국어 */
    public String name(Lang lang) { return lang.pick(name, nameEn); }
    public String description(Lang lang) { return lang.pick(description, descriptionEn); }

    /** 영어판 채우기(DataSeeder 뒤 EnglishBackfill) */
    public void changeEnglish(String nameEn, String descriptionEn) { this.nameEn = nameEn; this.descriptionEn = descriptionEn; }

    /** 표시 순서. 시드가 아직 처음 값 그대로인 행만 새 순서로 바꿀 때 쓴다 */
    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }

    public Title(String name, String description, String conditionType, String conditionParams, int sortOrder) {
        this.name = name; this.description = description; this.conditionType = conditionType;
        this.conditionParams = conditionParams; this.sortOrder = sortOrder;
    }
}
