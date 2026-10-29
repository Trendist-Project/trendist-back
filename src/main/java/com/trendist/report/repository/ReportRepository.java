package com.trendist.report.repository;

import com.trendist.report.entity.Report;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReportTypeIdAndTargetIdAndPlayerId(Long reportTypeId, long targetId, Long playerId);

    /** 관리자 신고 목록(13.5): 상태로 거르고(null이면 전체) 최신순. */
    @Query(value = """
            select r from Report r join fetch r.player join fetch r.reportType
            where (:status is null or r.status = :status) order by r.createdAt desc, r.id desc""",
            countQuery = "select count(r) from Report r where (:status is null or r.status = :status)")
    Page<Report> findForAdmin(@Param("status") String status, Pageable pageable);

    long countByReportTypeIdAndTargetId(Long reportTypeId, long targetId);

    List<Report> findByReportTypeIdAndTargetIdAndStatus(Long reportTypeId, long targetId, String status);
}
