package com.trendist.skill.repository;

import com.trendist.skill.entity.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    /** 목록 순서는 sort_order(시더가 기본 해금 순서대로 매긴 값) 그대로다. 해금 레벨을 고쳐도 순서는 바뀌지 않는다 */
    List<Skill> findAllByOrderBySortOrderAscIdAsc();

    Optional<Skill> findByCode(String code);
}
