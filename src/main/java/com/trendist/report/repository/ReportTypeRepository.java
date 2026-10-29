package com.trendist.report.repository;

import com.trendist.report.entity.ReportType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportTypeRepository extends JpaRepository<ReportType, Long> {
    Optional<ReportType> findByName(String name);
}
