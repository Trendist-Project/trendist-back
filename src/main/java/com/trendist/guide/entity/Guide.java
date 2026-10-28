package com.trendist.guide.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게임 가이드 글(4.17). 본문은 마크다운(허용 문법만), FAQ형은 제목이 질문이고 본문이 답이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guides", indexes = @Index(name = "idx_guides_list", columnList = "guide_category_id, deleted_at, sort_order"))
public class Guide extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guide_category_id", nullable = false) private GuideCategory guideCategory;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String body;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)") private Instant deletedAt;

    public Guide(GuideCategory category, User author, String title, String body, int sortOrder) {
        this.guideCategory = category; this.user = author; this.title = title; this.body = body; this.sortOrder = sortOrder;
    }
}
