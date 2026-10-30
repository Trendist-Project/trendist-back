package com.trendist.guide.repository;

import com.trendist.guide.entity.GuideCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideCategoryRepository extends JpaRepository<GuideCategory, Long> {
    List<GuideCategory> findByEnabledTrueOrderBySortOrderAscIdAsc();

    Optional<GuideCategory> findByCodeAndEnabledTrue(String code);

    List<GuideCategory> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);
}
