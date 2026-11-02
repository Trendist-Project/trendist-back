package com.trendist.effect.repository;

import com.trendist.effect.entity.Effect;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EffectRepository extends JpaRepository<Effect, Long> {
    /** 그 종류의 쓰는(enabled) 효과, 순서대로. */
    @Query("select e from Effect e join fetch e.effectType t where t.code = :type and e.enabled = true order by e.sortOrder asc, e.id asc")
    List<Effect> findEnabled(@Param("type") String typeCode);

    @Query("select e from Effect e join fetch e.effectType t where t.code = :type and e.code = :code")
    Optional<Effect> findByTypeAndCode(@Param("type") String typeCode, @Param("code") String code);

    @Query("select e from Effect e join fetch e.effectType where e.id = :id")
    Optional<Effect> findWithType(@Param("id") Long id);
}
