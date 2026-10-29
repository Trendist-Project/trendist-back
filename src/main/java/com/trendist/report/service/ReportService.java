package com.trendist.report.service;

import java.time.Instant;
import java.util.List;

/** 신고 접수와 관리자 검토(13.5). 댓글·채팅·플레이어 신고를 받는다. */
public interface ReportService {
    /**
     * 신고를 남긴다(댓글·채팅은 대상의 신고 수도 1 올린다). 내 나라의 대상만, 자기 것은 안 되고, 같은 대상은 한 번만(409 REPORT_DUPLICATE).
     *
     * @param target   COMMENT / CHAT / PLAYER
     * @param targetId 댓글 번호, 채팅 메시지 번호, 또는 PLAYER면 같은 방의 세션 엔티티 번호(서버가 플레이어 번호로 바꿔 저장한다)
     */
    void report(long userId, String target, long targetId, String reason);

    /**
     * 관리자 신고 목록의 한 줄. type은 신고 유형 이름(댓글·채팅·플레이어), content는 신고된 글(플레이어면 null),
     * targetNickname은 신고된 사람, sameTargetCount는 같은 대상에 들어온 신고 수.
     */
    record AdminReport(long id, String type, long targetId, String reason, String status, Instant createdAt, Instant updatedAt,
                       String reporterNickname, String targetNickname, String content, String keywordText, long sameTargetCount) {}

    record AdminReportPage(List<AdminReport> items, long total, int page, int size) {}

    /** status: RECEIVED / RESOLVED / REJECTED(비우면 전체), 최신순. */
    AdminReportPage adminList(String status, int page, int size);

    /** 처리 결과를 정한다. sameTarget이면 같은 대상의 아직 처리하지 않은 신고도 함께 바꾼다. 바꾼 신고 수를 돌려준다. */
    int adminSetStatus(long reportId, String status, boolean sameTarget);
}
