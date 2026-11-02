package com.trendist.user.repository;

import com.trendist.user.entity.SanctionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionTypeRepository extends JpaRepository<SanctionType, Long> {
    Optional<SanctionType> findByName(String name);
}
