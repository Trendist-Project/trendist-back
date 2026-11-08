package com.trendist.skill.service;

import com.trendist.skill.dto.SkillDtos;
import java.util.List;
import java.util.Set;

/** 스킬 데이터(4.33)를 읽는 곳. DB 값은 잠깐(10초) 기억해 두고 쓴다. */
public interface SkillCatalog {
    /** 쓸 수 있는(사용 여부가 켜진) 스킬 목록, 표시 순서대로 */
    List<SkillDtos.Item> list();

    /** 그 스킬의 쿨타임(초). 행이 없으면 fallback */
    double cooldownSeconds(String code, double fallback);

    /** 스킬 코드 → 해금 레벨(1보다 큰 것만). 이 레벨이 안 되면 서버가 쓰지 못하게 한다 */
    java.util.Map<String, Integer> requiredLevels();

    /** 사용 여부가 꺼진 스킬 코드 */
    Set<String> disabledCodes();

    /** 기억해 둔 값을 버린다(DB를 직접 고친 뒤 바로 반영하려고) */
    void invalidate();
}
