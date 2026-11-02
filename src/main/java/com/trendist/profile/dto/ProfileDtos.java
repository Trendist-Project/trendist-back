package com.trendist.profile.dto;

import com.trendist.effect.support.EffectRules;
import com.trendist.game.room.Room;
import java.time.Instant;
import java.util.List;

/** 꾸미기 프로필(4.9). */
public final class ProfileDtos {
    private ProfileDtos() {}

    /**
     * 원의 모습. borderColor·borderWidth가 null이면 기본 테두리(1~8단계). art가 있으면 성장 캐릭터 대신 그 그림(픽셀 그림 이미지 주소).
     * borderEffect·trailEffect는 테두리 효과·이동 효과 코드, nameColor·nameStyle은 이름표 글자색과 모양(RIBBON / GLOW). null이면 기본.
     */
    public record Look(String background, String borderColor, Integer borderWidth, String art,
                       String borderEffect, String nameColor, String nameStyle, String trailEffect) {
        /** 방에 보낼 원의 모습(이동 효과는 따로 보낸다) */
        public Room.Skin toSkin() { return new Room.Skin(background, borderColor, borderWidth, art, borderEffect, nameColor, nameStyle); }
    }

    /** 픽셀 그림: palette는 #rrggbb 목록, pixels는 32×32 = 1024글자(팔레트 번호 0-9a-v, '.' 투명). */
    public record Art(List<String> palette, String pixels) {}

    /**
     * 프로필 한 칸. saved가 false면 아직 저장하지 않은 칸(기본 모습). inner: GROWTH(성장 캐릭터) / PIXEL(내 그림),
     * art는 그 칸에 저장한 그림(없으면 null), trailEffectId·borderEffectId는 고른 효과 번호.
     */
    public record Slot(int slot, boolean saved, boolean equipped, String inner, Look look, Art art,
                       Long trailEffectId, Long borderEffectId, Instant savedAt) {}

    /**
     * 내 꾸미기 프로필 전부. unlocked: 꾸밀 수 있는 레벨인지(해금 레벨, 그림·효과는 그중 로그인 사용자만),
     * colors: 프리셋 24색, canDraw: 그림을 저장할 수 있는지와 drawLockReason, artChangeableAt: 다음에 그림을 바꿀 수 있는 시각,
     * trails·borders: 고를 수 있는 이동 효과·테두리 효과(잠긴 것은 lockReason).
     */
    public record Box(List<Slot> slots, int equippedSlot, boolean unlocked, int unlockLevel, Look defaults, List<String> colors,
                      List<String> nameStyles, boolean canDraw, String drawLockReason, Instant artChangeableAt, int artSize, int maxColors,
                      List<EffectRules.Option> trails, List<EffectRules.Option> borders) {}

    /** 한 칸 저장. art가 null이면 그 칸의 그림을 그대로 둔다. 효과 번호가 null이면 끈다. equip이면 저장한 뒤 이 칸을 장착한다. */
    public record SaveRequest(String background, String borderColor, Integer borderWidth, String nameColor, String nameStyle,
                              String inner, Art art, Long trailEffectId, Long borderEffectId, Boolean equip) {}

    public record EquipRequest(int slot) {}
}
