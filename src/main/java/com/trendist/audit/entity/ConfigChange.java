package com.trendist.audit.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설정 변경 이력(13.17): 바뀐 값 하나마다 한 줄. scope는 SETTING(밸런스 설정)·MONSTER(몬스터 설정)·SKILL(스킬 설정),
 * target은 대상(설정 이름 / 몬스터코드/설정이름 / 몬스터코드 / 스킬코드), field는 바뀐 항목(value·enabled·cooldown·level),
 * 값은 문자열(숫자는 불필요한 0 없이, 켜기·끄기는 true·false)이다. revertedFromId가 있으면 그 줄을 되돌린 변경이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "config_changes")
public class ConfigChange extends BaseTimeEntity {
    @Column(nullable = false, length = 20) private String scope;
    @Column(nullable = false, length = 120) private String target;
    @Column(nullable = false, length = 30) private String field;
    @Column(name = "old_value", length = 100) private String oldValue;
    @Column(name = "new_value", length = 100) private String newValue;
    @Column(name = "admin_user_id", nullable = false) private Long adminUserId;
    @Column(name = "admin_name", length = 60) private String adminName;
    @Column(length = 300) private String memo;
    @Column(name = "reverted_from_id") private Long revertedFromId;

    public ConfigChange(String scope, String target, String field, String oldValue, String newValue, Long adminUserId, String adminName, Long revertedFromId) {
        this.scope = scope; this.target = target; this.field = field; this.oldValue = oldValue; this.newValue = newValue;
        this.adminUserId = adminUserId; this.adminName = adminName; this.revertedFromId = revertedFromId;
    }

    public void changeMemo(String memo) { this.memo = memo; }
}
