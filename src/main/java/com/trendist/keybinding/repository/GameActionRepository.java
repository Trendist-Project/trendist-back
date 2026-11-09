package com.trendist.keybinding.repository;

import com.trendist.keybinding.entity.GameAction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {
    Optional<GameAction> findByCode(String code);

    /** 스킬 장착 칸(종류가 SKILL_SLOT인 동작), 칸 번호(param) 순서. 칸 수는 이 목록의 크기다 */
    @Query("select a from GameAction a join fetch a.type t where t.code = 'SKILL_SLOT' order by a.param, a.id")
    List<GameAction> findSkillSlotActions();

    @Query("select a from GameAction a join fetch a.type left join fetch a.defaultKey dk left join fetch dk.aliasOf order by a.sortOrder, a.id")
    List<GameAction> findAllWithTypeAndDefaultKey();
}
