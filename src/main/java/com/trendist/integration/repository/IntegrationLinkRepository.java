package com.trendist.integration.repository;

import com.trendist.integration.entity.IntegrationLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationLinkRepository extends JpaRepository<IntegrationLink, Long> {
    List<IntegrationLink> findAllByOrderBySortOrderAsc();
    Optional<IntegrationLink> findByCode(String code);
}
