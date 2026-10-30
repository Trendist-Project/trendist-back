package com.trendist.effect.service;

import com.trendist.effect.dto.EffectDtos;

public interface EffectService {
    /** 사용할 수 있는 모든 이동 효과와 얻었는지, 지금 쓰는 효과. */
    EffectDtos.Box box(long userId);

    /** 이동 효과를 바꾼다(null이면 끈다). 얻지 못한 효과는 403, 5초에 1번. */
    EffectDtos.Box choose(long userId, Long effectId);
}
