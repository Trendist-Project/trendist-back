package com.trendist.effect.repository;

import com.trendist.effect.entity.EffectType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EffectTypeRepository extends JpaRepository<EffectType, Long> {
    Optional<EffectType> findByCode(String code);
}
