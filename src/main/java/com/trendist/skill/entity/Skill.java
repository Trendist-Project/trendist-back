package com.trendist.skill.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스킬(4.33). 동작은 코드(Room.SKILLS)가 정하고, 여기서는 쓰는 법을 DB로 운영한다:
 * 코드, 아이콘, 종류(ATTACK 공격 / DEFENSE 방어 / CC 군중 제어 / UTILITY 유틸), 기본 단축키(KeyboardEvent.code, 예 Digit1·Space), 쿨타임(초), 해금 레벨(1이면 처음부터), 사용 여부, 순서.
 * 이름·설명은 번역이 필요한 화면 문구라서 DB에 두지 않고 프론트엔드 번역 파일(skills.<코드>_name·_desc)에 둔다.
 * 사용자가 키를 바꾸는 기능은 이 기본 키 위에 덧씌운다(클라이언트의 설정, 서버는 기본 키만 안다).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "skills", uniqueConstraints = @UniqueConstraint(name = "uk_skills_code", columnNames = "code"))
public class Skill extends BaseTimeEntity {
    @Column(nullable = false, length = 30) private String code;
    @Column(length = 20) private String icon;
    @Column(nullable = false, length = 20) private String category;
    @Column(name = "default_key", nullable = false, length = 30) private String defaultKey;
    @Column(name = "cooldown_seconds", nullable = false, precision = 10, scale = 2) private BigDecimal cooldownSeconds;
    @Column(name = "required_level", nullable = false) private int requiredLevel = 1;
    @Column(name = "sort_order", nullable = false) private long sortOrder;
    @Column(nullable = false) private boolean enabled = true;

    /** 관리자가 바꾸는 값(13.3): 쿨타임(초, 0이면 없음), 해금 레벨, 사용 여부 */
    public void changeCooldown(BigDecimal seconds) { this.cooldownSeconds = seconds; }
    public void changeRequiredLevel(int level) { this.requiredLevel = level; }
    public void changeEnabled(boolean enabled) { this.enabled = enabled; }
    /** 목록 순서는 코드(시더)가 정한다 */
    public void changeSortOrder(long sortOrder) { this.sortOrder = sortOrder; }

    public Skill(String code, String icon, String category, String defaultKey, double cooldownSeconds, int requiredLevel, long sortOrder) {
        this.code = code; this.requiredLevel = requiredLevel;
        this.icon = icon; this.category = category; this.defaultKey = defaultKey;
        this.cooldownSeconds = BigDecimal.valueOf(cooldownSeconds); this.sortOrder = sortOrder;
    }
}
