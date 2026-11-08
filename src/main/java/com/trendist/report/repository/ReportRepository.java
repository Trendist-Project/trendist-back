package com.trendist.report.repository;

import com.trendist.report.entity.Report;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReportTypeIdAndTargetIdAndPlayerIdAndReportDate(Long reportTypeId, long targetId, Long playerId, java.time.LocalDate reportDate);

    /** 관리자 신고 목록(13.5): 상태로 거르고(null이면 전체) 최신순. */
    @Query(value = """
            select r from Report r join fetch r.player join fetch r.reportType
            where (:status is null or r.status = :status) order by r.createdAt desc, r.id desc""",
            countQuery = "select count(r) from Report r where (:status is null or r.status = :status)")
    Page<Report> findForAdmin(@Param("status") String status, Pageable pageable);

    long countByReportTypeIdAndTargetId(Long reportTypeId, long targetId);

    long countByStatus(String status);

    /** 관리자 사용자 관리(13.10): 플레이어별 받은 신고 수. 플레이어 신고 + 그 사람 댓글·채팅에 들어온 신고. [플레이어 번호, 수] */
    @Query("select r.targetId, count(r) from Report r where r.reportType.name = '플레이어' and r.targetId in :ids group by r.targetId")
    List<Object[]> countPlayerReports(@Param("ids") java.util.Collection<Long> ids);

    @Query("select c.player.id, count(r) from Report r, Comment c where r.reportType.name = '댓글' and r.targetId = c.id and c.player.id in :ids group by c.player.id")
    List<Object[]> countCommentReports(@Param("ids") java.util.Collection<Long> ids);

    @Query("select m.player.id, count(r) from Report r, ChatMessage m where r.reportType.name = '채팅' and r.targetId = m.id and m.player.id in :ids group by m.player.id")
    List<Object[]> countChatReports(@Param("ids") java.util.Collection<Long> ids);

    /** 한 플레이어가 받은 신고(최신순). */
    @Query("""
            select r from Report r join fetch r.player join fetch r.reportType
            where (r.reportType.name = '플레이어' and r.targetId = :playerId)
               or (r.reportType.name = '댓글' and r.targetId in (select c.id from Comment c where c.player.id = :playerId))
               or (r.reportType.name = '채팅' and r.targetId in (select m.id from ChatMessage m where m.player.id = :playerId))
            order by r.id desc""")
    List<Report> findAgainstPlayer(@Param("playerId") Long playerId, Pageable pageable);

    List<Report> findByReportTypeIdAndTargetIdAndStatus(Long reportTypeId, long targetId, String status);

    /** 관리자 대시보드(13.11): since 뒤에 받은 신고를 대상 플레이어별로 센다(플레이어·댓글·채팅 신고). [플레이어 번호, 수] */
    @Query("select r.targetId, count(r) from Report r where r.reportType.name = '플레이어' and r.createdAt >= :since group by r.targetId")
    List<Object[]> countPlayerReportsSince(@Param("since") java.time.Instant since);

    @Query("select c.player.id, count(r) from Report r, Comment c where r.reportType.name = '댓글' and r.targetId = c.id and r.createdAt >= :since group by c.player.id")
    List<Object[]> countCommentReportsSince(@Param("since") java.time.Instant since);

    @Query("select m.player.id, count(r) from Report r, ChatMessage m where r.reportType.name = '채팅' and r.targetId = m.id and r.createdAt >= :since group by m.player.id")
    List<Object[]> countChatReportsSince(@Param("since") java.time.Instant since);
}
