package com.trendist.report.service;

/** 신고 접수(13.5). 지금은 댓글과 채팅만 받는다. */
public interface ReportService {
    /**
     * 신고를 남기고 대상의 신고 수를 1 올린다. 내 나라의 대상만, 자기 것은 안 되고, 같은 대상은 한 번만(409 REPORT_DUPLICATE).
     *
     * @param target COMMENT / CHAT
     */
    void report(long userId, String target, long targetId, String reason);
}
