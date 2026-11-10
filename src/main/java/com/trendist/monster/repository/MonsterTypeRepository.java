package com.trendist.monster.repository;

import com.trendist.monster.entity.MonsterType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterTypeRepository extends JpaRepository<MonsterType, Long> {
    Optional<MonsterType> findByCode(String code);
    List<MonsterType> findAllByOrderBySortOrderAscIdAsc();
}
