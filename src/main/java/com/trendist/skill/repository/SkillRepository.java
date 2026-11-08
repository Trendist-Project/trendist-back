package com.trendist.skill.repository;

import com.trendist.skill.entity.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findAllByOrderBySortOrderAscIdAsc();

    Optional<Skill> findByCode(String code);
}
