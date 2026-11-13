package com.trendist.guide.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.user.entity.User;
import com.trendist.common.support.Lang;
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
    @Column(name = "title_en", length = 100) private String titleEn;
    @Column(name = "body_en", columnDefinition = "MEDIUMTEXT") private String bodyEn;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)") private Instant deletedAt;
    /** 시드 글(4.17): 이 행이 따르는 시드 판 번호(GuideSeed.VERSIONS)와, 시더가 마지막으로 넣은 본문(한·영)의 해시. 관리자가 만든 글은 둘 다 NULL */
    @Column(name = "seed_version") private Integer seedVersion;
    @Column(name = "seed_hash", length = 64) private String seedHash;

    /** 지운 글(소프트 삭제). 목록에서 빠진다 */
    public void delete(Instant now) { this.deletedAt = now; }
    /** 시드 글 고침(DataSeeder): 본문만 바꾼다 */
    public void changeBody(String body) { this.body = body; }
    /** 관리자 수정(13.4) */
    public void edit(String title, String body) { this.title = title; this.body = body; }
    /** 관리자 수정(영어판 포함) */
    public void edit(String title, String titleEn, String body, String bodyEn) {
        edit(title, body); this.titleEn = titleEn; this.bodyEn = bodyEn;
    }
    public String title(Lang lang) { return lang.pick(title, titleEn); }
    public String body(Lang lang) { return lang.pick(body, bodyEn); }
    /** 영어판 채우기(EnglishBackfill) */
    public void changeEnglish(String titleEn, String bodyEn) { this.titleEn = titleEn; this.bodyEn = bodyEn; }
    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }
    /** 시더가 이 글을 seedVersion판으로 봤다. hash는 그때 시드 본문의 해시(관리자가 고친 글이면 예전 해시를 그대로 둔다) */
    public void markSeed(Integer seedVersion, String hash) { this.seedVersion = seedVersion; this.seedHash = hash; }

    public Guide(GuideCategory category, User author, String title, String body, int sortOrder) {
        this.guideCategory = category; this.user = author; this.title = title; this.body = body; this.sortOrder = sortOrder;
    }
}
