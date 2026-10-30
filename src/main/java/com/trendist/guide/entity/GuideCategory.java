package com.trendist.guide.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게임 가이드 카테고리(4.17). 보여 주는 형식은 FAQ(아코디언) 또는 ARTICLE(문서형). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guide_categories", uniqueConstraints = @UniqueConstraint(name = "uk_guide_categories_code", columnNames = "code"))
public class GuideCategory extends BaseTimeEntity {
    public static final String FAQ = "FAQ";
    public static final String ARTICLE = "ARTICLE";

    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "display_type", nullable = false, length = 20) private String displayType;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    /** 시드가 예전 기본 글을 새 글로 바꿀 때 이름·형식·순서를 함께 바꾼다 */
    public void change(String name, String displayType, long sortOrder) {
        this.name = name; this.displayType = displayType; this.sortOrder = sortOrder;
    }

    public GuideCategory(String code, String name, String displayType, int sortOrder) {
        this.code = code; this.name = name; this.displayType = displayType; this.sortOrder = sortOrder;
    }
}
