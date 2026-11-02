package com.trendist.user.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제재 타입(13.5): 계정 금지 / 이미지 업로드 정지 / 댓글 작성 정지 / 채팅 정지. 애플리케이션이 이름으로 구분한다(11.1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sanction_types", uniqueConstraints = @UniqueConstraint(name = "uk_sanction_types_name", columnNames = "name"))
public class SanctionType extends BaseTimeEntity {
    public static final String BAN = "계정 금지";
    public static final String UPLOAD = "이미지 업로드 정지";
    public static final String COMMENT = "댓글 작성 정지";
    public static final String CHAT = "채팅 정지";
    /** 시드 순서(ID가 정해지는 순서). 새 종류는 뒤에만 붙인다 */
    public static final java.util.List<String> ALL = java.util.List.of(BAN, UPLOAD, COMMENT, CHAT);

    /** 기간은 걸 때 관리자가 고른다(1·3·7·30일·영구, 13.5) */
    @Column(nullable = false, length = 100) private String name;

    public SanctionType(String name) { this.name = name; }
}
