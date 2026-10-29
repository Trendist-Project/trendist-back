package com.trendist.report.repository;

import com.trendist.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReportTypeIdAndTargetIdAndPlayerId(Long reportTypeId, long targetId, Long playerId);
}
