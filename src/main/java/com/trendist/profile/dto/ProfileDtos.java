package com.trendist.profile.dto;

import java.util.List;

/** 꾸미기(4.9). */
public final class ProfileDtos {
    private ProfileDtos() {}

    /** 원의 모습. borderColor·borderWidth가 null이면 기본 테두리. borderWidth는 1~8단계. */
    public record Look(String background, String borderColor, Integer borderWidth) {}

    /** 내 꾸미기: 지금 모습, 열렸는지(레벨), 해금 레벨, 기본 모습, 프리셋 색. */
    public record Mine(Look look, boolean unlocked, int unlockLevel, Look defaults, List<String> presets) {}

    public record UpdateRequest(String background, String borderColor, Integer borderWidth) {}
}
