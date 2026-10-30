package com.trendist.effect.dto;

import java.util.List;

/** 이동 효과 고르기(4.23). */
public final class EffectDtos {
    private EffectDtos() {}

    /** 효과 하나. unlocked가 false면 lockReason에 얻는 조건을 사람이 읽는 말로 담는다. */
    public record Item(long id, String code, String name, String description, String unlockType, Long unlockValue,
                       boolean unlocked, String lockReason, boolean active) {}

    public record Box(Long activeEffectId, List<Item> effects) {}

    public record ChooseRequest(Long effectId) {}
}
