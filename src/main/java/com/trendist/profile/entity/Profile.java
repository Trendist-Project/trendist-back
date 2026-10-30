package com.trendist.profile.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 꾸미기 프로필(4.9). 플레이어 하나에 여러 개를 둘 수 있고 하나만 장착한다. 지금은 성장형(GROWTH)의 색과 테두리만 바꾼다.
 * 색은 모두 #RRGGBB, 테두리 색·두께가 null이면 기본값이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "profiles", indexes = @Index(name = "idx_profiles_player", columnList = "player_id, equipped"))
public class Profile extends BaseTimeEntity {
    public static final String GROWTH = "GROWTH";

    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(nullable = false) private boolean equipped;
    @Column(name = "background_color", nullable = false, length = 7) private String backgroundColor;
    @Column(name = "border_color", length = 7) private String borderColor;
    @Column(name = "border_width", columnDefinition = "TINYINT UNSIGNED") private Integer borderWidth;
    @Column(name = "profile_type", nullable = false, length = 20) private String profileType = GROWTH;
    @Column(name = "image_changed_at", columnDefinition = "DATETIME(6)") private Instant imageChangedAt;

    public Profile(long playerId, String backgroundColor) {
        this.playerId = playerId;
        this.backgroundColor = backgroundColor;
        this.equipped = true;
    }

    /** 색과 테두리를 바꾼다. 검사는 서비스가 한다. */
    public void changeLook(String backgroundColor, String borderColor, Integer borderWidth) {
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
    }
}
