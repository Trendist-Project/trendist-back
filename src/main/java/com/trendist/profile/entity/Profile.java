package com.trendist.profile.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 꾸미기 프로필(4.9). 플레이어마다 1~3번 칸이 있고 칸마다 모든 꾸미기를 담는다: 바탕색, 테두리 색·두께, 이름표 글자색·모양,
 * 원 안에 그릴 것(profile_type: GROWTH 성장 캐릭터 / PIXEL 내 그림)과 그 칸의 픽셀 그림, 이동 효과, 테두리 효과.
 * 한 칸만 장착한다(equipped). 저장한 칸이 없으면 1번 칸(기본 모습)을 장착한 것으로 본다.
 * 색은 모두 #RRGGBB, null이면 기본값이다. 픽셀 그림은 32×32 칸: pixel_palette는 쓴 색(쉼표로, 최대 32개),
 * pixel_data는 칸마다 한 글자(팔레트 번호 0-9a-v, '.'은 투명 = 바탕색).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_profiles", columnNames = {"player_id", "slot"}),
        indexes = @Index(name = "idx_profiles_player", columnList = "player_id, equipped"))
public class Profile extends BaseTimeEntity {
    public static final String GROWTH = "GROWTH";
    public static final String PIXEL = "PIXEL";
    public static final int SLOTS = 3;

    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(nullable = false) private long slot;
    @Column(nullable = false) private boolean equipped;
    @Column(name = "background_color", nullable = false, length = 7) private String backgroundColor;
    @Column(name = "border_color", length = 7) private String borderColor;
    @Column(name = "border_width") private Long borderWidth;
    @Column(name = "name_color", length = 7) private String nameColor;
    @Column(name = "name_style", length = 20) private String nameStyle;
    @Column(name = "profile_type", nullable = false, length = 20) private String profileType = GROWTH;
    @Column(name = "pixel_palette", length = 300) private String pixelPalette;
    @Column(name = "pixel_data", length = 1024) private String pixelData;
    @Column(name = "image_changed_at", columnDefinition = "DATETIME(6)") private Instant imageChangedAt;
    @Column(name = "trail_effect_id") private Long trailEffectId;
    @Column(name = "border_effect_id") private Long borderEffectId;

    public Profile(long playerId, long slot, String backgroundColor) {
        this.playerId = playerId;
        this.slot = slot;
        this.backgroundColor = backgroundColor;
    }

    /** 모습을 바꾼다. 검사는 서비스가 한다. */
    public void changeLook(String backgroundColor, String borderColor, Long borderWidth, String nameColor, String nameStyle, String profileType) {
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.nameColor = nameColor;
        this.nameStyle = nameStyle;
        this.profileType = profileType;
    }

    /** 그림을 바꾼다. 바꾼 시각은 변경 간격 확인과 이미지 주소의 버전(?v=)에 쓴다. */
    public void changeArt(String palette, String data, Instant now) {
        this.pixelPalette = palette;
        this.pixelData = data;
        this.imageChangedAt = now;
    }

    public void changeEffects(Long trailEffectId, Long borderEffectId) {
        this.trailEffectId = trailEffectId;
        this.borderEffectId = borderEffectId;
    }

    public void setEquipped(boolean equipped) { this.equipped = equipped; }

    public boolean hasArt() { return pixelData != null; }

    /** 원 안에 내 그림을 그리는지(고른 것이 PIXEL이고 그림이 있을 때) */
    public boolean showsArt() { return PIXEL.equals(profileType) && hasArt(); }

    /** 픽셀 그림 이미지 주소. 그림을 바꾸면 버전이 바뀌어 옛 이미지가 캐시에 남지 않는다. 그림이 없으면 null. */
    public String artUrl() {
        return hasArt() ? "/api/art/" + id + ".png?v=" + imageChangedAt.toEpochMilli() : null;
    }
}
