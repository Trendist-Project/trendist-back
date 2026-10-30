package com.trendist.effect.repository;

import com.trendist.effect.entity.TrailEffect;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrailEffectRepository extends JpaRepository<TrailEffect, Long> {
    List<TrailEffect> findByEnabledTrueOrderBySortOrderAsc();

    Optional<TrailEffect> findByCode(String code);
}
