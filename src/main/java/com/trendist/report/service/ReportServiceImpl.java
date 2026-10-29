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
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.player.repository.PlayerRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final PlayerRepository players;
    private final RoomManager rooms;

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
            case "PLAYER" -> {
                // 정보 팝업(4.11)에서 신고한다. 같은 방에 있을 때만, 세션 엔티티 번호를 플레이어 번호로 바꿔 저장한다
                long myCountry = me.getCountry().getId();
                if (rooms.liveScore(myCountry, me.getId()) == null) throw notInRoom();
                Room.Live live = rooms.livePlayerByEntity(myCountry, Math.toIntExact(targetId)).orElseThrow(ReportServiceImpl::notInRoom);
                typeName = ReportType.PLAYER;
                countryId = myCountry;
                ownerId = live.playerId();
                targetId = live.playerId();
            }
            default -> throw ApiException.badRequest("REPORT_TARGET", "신고할 수 없는 대상이에요");
        }
        if (countryId != me.getCountry().getId()) throw notFound();
        if (ownerId == me.getId()) throw ApiException.badRequest("REPORT_OWN", "나는 신고할 수 없어요");

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
        else if (ReportType.CHAT.equals(typeName)) chats.incReportCount(targetId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportPage adminList(String status, int page, int size) {
        String st = status == null || status.isBlank() ? null : status;
        if (st != null && !Report.STATUSES.contains(st)) throw ApiException.badRequest("REPORT_STATUS", "알 수 없는 처리 상태예요");
        int sz = Math.max(1, Math.min(size, 100));
        Page<Report> result = reports.findForAdmin(st, PageRequest.of(Math.max(0, page), sz));
        List<AdminReport> items = result.getContent().stream().map(this::adminView).toList();
        return new AdminReportPage(items, result.getTotalElements(), result.getNumber(), sz);
    }

    /** 신고 한 줄에 대상의 내용(댓글·채팅 본문, 신고된 사람)을 붙인다. 지운 글도 관리자는 원문을 본다(13.5). */
    private AdminReport adminView(Report r) {
        String type = r.getReportType().getName();
        String target = null, content = null, keyword = null;
        switch (type) {
            case ReportType.COMMENT -> {
                var c = comments.findById(r.getTargetId()).orElse(null);
                if (c != null) { target = c.getPlayer().getNickname(); content = c.getBody(); keyword = c.getKeyword().getText(); }
            }
            case ReportType.CHAT -> {
                var m = chats.findById(r.getTargetId()).orElse(null);
                if (m != null) { target = m.getPlayer().getNickname(); content = m.getBody(); }
            }
            case ReportType.PLAYER -> target = players.findById(r.getTargetId()).map(Player::getNickname).orElse(null);
            default -> { }
        }
        return new AdminReport(r.getId(), type, r.getTargetId(), r.getReason(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getPlayer().getNickname(), target, content, keyword,
                reports.countByReportTypeIdAndTargetId(r.getReportType().getId(), r.getTargetId()));
    }

    @Override
    @Transactional
    public int adminSetStatus(long reportId, String status, boolean sameTarget) {
        if (!Report.STATUSES.contains(status)) throw ApiException.badRequest("REPORT_STATUS", "알 수 없는 처리 상태예요");
        Report r = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "신고를 찾을 수 없어요"));
        r.changeStatus(status);
        int changed = 1;
        if (sameTarget) {
            for (Report o : reports.findByReportTypeIdAndTargetIdAndStatus(r.getReportType().getId(), r.getTargetId(), Report.RECEIVED)) {
                if (o.getId().equals(r.getId())) continue;
                o.changeStatus(status);
                changed++;
            }
        }
        return changed;
    }

    private static ApiException notInRoom() { return ApiException.notFound("PLAYER_NOT_IN_ROOM", "지금 방에 없는 플레이어예요"); }

    private static ApiException notFound() { return ApiException.notFound("REPORT_TARGET_NOT_FOUND", "신고할 글을 찾을 수 없어요"); }

    private static ApiException duplicate() { return ApiException.conflict("REPORT_DUPLICATE", "이미 신고했어요"); }
}
