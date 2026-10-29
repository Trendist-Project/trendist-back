package com.trendist.report.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신고 대상 유형(13.5): 키워드 / 댓글 / 아이콘 이미지 / 플레이어 / 채팅. 애플리케이션이 이름으로 구분한다(11.1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "report_types", uniqueConstraints = @UniqueConstraint(name = "uk_report_types_name", columnNames = "name"))
public class ReportType extends BaseTimeEntity {
    public static final String KEYWORD = "키워드";
    public static final String COMMENT = "댓글";
    public static final String ICON_IMAGE = "아이콘 이미지";
    public static final String PLAYER = "플레이어";
    public static final String CHAT = "채팅";
    /** 시드 순서(ID가 정해지는 순서). */
    public static final java.util.List<String> ALL = java.util.List.of(KEYWORD, COMMENT, ICON_IMAGE, PLAYER, CHAT);

    @Column(nullable = false, length = 50) private String name;

    public ReportType(String name) { this.name = name; }
}
