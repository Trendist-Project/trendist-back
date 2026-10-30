package com.trendist.guide.repository;

import com.trendist.guide.entity.Guide;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    List<Guide> findByGuideCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long guideCategoryId);

    List<Guide> findByGuideCategoryId(Long guideCategoryId);
}
