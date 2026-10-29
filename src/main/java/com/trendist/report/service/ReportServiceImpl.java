package com.trendist.report.service;

import com.trendist.chat.entity.ChatMessage;
import com.trendist.chat.repository.ChatMessageRepository;
import com.trendist.comment.entity.Comment;
import com.trendist.comment.repository.CommentRepository;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.TextRules;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
import com.trendist.report.entity.Report;
import com.trendist.report.entity.ReportType;
import com.trendist.report.repository.ReportRepository;
import com.trendist.report.repository.ReportTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private final ReportRepository reports;
    private final ReportTypeRepository types;
    private final CommentRepository comments;
    private final ChatMessageRepository chats;
    private final PlayerService playerService;

    @Override
    @Transactional
    public void report(long userId, String target, long targetId, String rawReason) {
        Player me = playerService.requireCurrent(userId);
        String typeName;
        long countryId, ownerId;
        switch (target) {
            case "COMMENT" -> {
                Comment c = comments.findById(targetId).orElseThrow(ReportServiceImpl::notFound);
                typeName = ReportType.COMMENT;
                countryId = c.getKeyword().getCountry().getId();
                ownerId = c.getPlayer().getId();
            }
            case "CHAT" -> {
                ChatMessage m = chats.findById(targetId).orElseThrow(ReportServiceImpl::notFound);
                typeName = ReportType.CHAT;
                countryId = m.getCountry().getId();
                ownerId = m.getPlayer().getId();
            }
            default -> throw ApiException.badRequest("REPORT_TARGET", "신고할 수 없는 대상이에요");
        }
        if (countryId != me.getCountry().getId()) throw notFound();
        if (ownerId == me.getId()) throw ApiException.badRequest("REPORT_OWN", "내 글은 신고할 수 없어요");

        ReportType type = types.findByName(typeName).orElseThrow(() -> new IllegalStateException("report type missing: " + typeName));
        if (reports.existsByReportTypeIdAndTargetIdAndPlayerId(type.getId(), targetId, me.getId())) throw duplicate();
        String reason = rawReason == null ? "" : TextRules.clean(rawReason, true); // 길이(500자)는 요청 검증이 막는다
        try {
            reports.saveAndFlush(new Report(me, type, targetId, reason.isEmpty() ? null : reason));
        } catch (DataIntegrityViolationException e) {
            throw duplicate(); // 동시에 두 번 누른 경우
        }
        // 대상의 신고 수: 관리자 검토 목록과 채팅 보관(신고된 메시지는 30일 뒤에도 남긴다)에 쓴다
        if (ReportType.COMMENT.equals(typeName)) comments.incReportCount(targetId);
        else chats.incReportCount(targetId);
    }

    private static ApiException notFound() { return ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고할 글을 찾을 수 없어요"); }

    private static ApiException duplicate() { return ApiException.conflict("REPORT_DUPLICATE", "이미 신고했어요"); }
}
