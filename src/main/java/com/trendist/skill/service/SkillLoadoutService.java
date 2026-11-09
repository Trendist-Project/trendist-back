package com.trendist.skill.service;

import java.util.List;

/**
 * 플레이어가 장착한 스킬(4.33). 칸은 actions 중 종류가 SKILL_SLOT인 동작이고(칸 번호 = param), 칸 수는 그 행의 수다.
 * 고른 적 없으면 null(해금된 스킬이 순서대로 채워진다), 골랐으면 칸 순서대로 스킬 코드(빈 칸은 null)를 돌려준다.
 */
public interface SkillLoadoutService {
    /** 칸 수(actions의 SKILL_SLOT 행 수). 방이 장착 칸 수를 정하는 기준이다 */
    int slotCount();

    /** 칸 순서대로 스킬 코드(빈 칸 null). 한 번도 고른 적 없으면 null */
    List<String> load(long playerId);

    /** 칸 순서대로 저장한다(빈 칸 null). 칸 수가 다르거나 모르는 스킬이면 예외 */
    void save(long playerId, List<String> codes);
}
