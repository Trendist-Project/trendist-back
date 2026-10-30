package com.trendist.notice.service;

import com.trendist.common.exception.ApiException;
import com.trendist.notice.dto.AdminNoticeDtos;
import com.trendist.notice.dto.NoticeItem;
import com.trendist.notice.entity.Notice;
import com.trendist.notice.repository.NoticeRepository;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {
    private static final int LIMIT = 50;
    private static final Duration FRESH = Duration.ofDays(7);        // 목록에서 NEW를 붙이는 기간(4.16)
    private static final Duration EDIT_GRACE = Duration.ofSeconds(1); // 등록 직후 저장 시각 차이는 수정으로 보지 않는다

    private static final int TITLE_MAX = 50, BODY_MAX = 5000;      // 글자 수(4.16)
    private static final int POPUP_DAYS_MIN = 1, POPUP_DAYS_MAX = 90, POPUP_DAYS_DEFAULT = 7;

    private final NoticeRepository notices;
    private final UserRepository users;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<NoticeItem> list() {
        Instant now = Instant.now(clock);
        return notices.findByDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, LIMIT)).stream()
                .map(n -> new NoticeItem(n.getId(), n.getTitle(), n.getBody(), n.isPinned(), n.getCreatedAt(),
                        n.getUpdatedAt().isAfter(n.getCreatedAt().plus(EDIT_GRACE)),
                        n.getCreatedAt().isAfter(now.minus(FRESH)),
                        n.isPopupEnabled() && n.getPopupUntil() != null && n.getPopupUntil().isAfter(now)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminNoticeDtos.Item> adminList() {
        return notices.findByDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, 200)).stream().map(this::item).toList();
    }

    @Override
    @Transactional
    public AdminNoticeDtos.Item create(long authorUserId, AdminNoticeDtos.Request req) {
        User author = users.findById(authorUserId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        Instant now = Instant.now(clock);
        Notice n = notices.save(new Notice(author, title(req), body(req), req.pinned(), req.popupEnabled(), popupUntil(req, now)));
        notices.flush();
        return item(n);
    }

    @Override
    @Transactional
    public AdminNoticeDtos.Item update(long id, AdminNoticeDtos.Request req) {
        Notice n = notices.findById(id).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "없는 공지예요"));
        // 팝업 기간은 등록 시각부터 센다(수정해도 기간이 새로 늘어나지 않는다)
        n.change(title(req), body(req), req.pinned(), req.popupEnabled(), popupUntil(req, n.getCreatedAt()));
        notices.flush();
        return item(n);
    }

    @Override
    @Transactional
    public void delete(long id) {
        Notice n = notices.findById(id).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("NOTICE_NOT_FOUND", "없는 공지예요"));
        n.delete(Instant.now(clock));
    }

    private static String title(AdminNoticeDtos.Request req) {
        String t = req.title() == null ? "" : req.title().strip();
        int len = t.codePointCount(0, t.length());
        if (len < 1 || len > TITLE_MAX) throw ApiException.badRequest("NOTICE_TITLE", "제목은 1~" + TITLE_MAX + "자로 써 주세요");
        return t;
    }

    private static String body(AdminNoticeDtos.Request req) {
        String b = req.body() == null ? "" : req.body().strip();
        int len = b.codePointCount(0, b.length());
        if (len < 1 || len > BODY_MAX) throw ApiException.badRequest("NOTICE_BODY", "본문은 1~" + BODY_MAX + "자로 써 주세요");
        return b;
    }

    private static Instant popupUntil(AdminNoticeDtos.Request req, Instant from) {
        if (!req.popupEnabled()) return null;
        int days = req.popupDays() == null ? POPUP_DAYS_DEFAULT : req.popupDays();
        if (days < POPUP_DAYS_MIN || days > POPUP_DAYS_MAX) {
            throw ApiException.badRequest("NOTICE_POPUP_DAYS", "팝업 기간은 " + POPUP_DAYS_MIN + "~" + POPUP_DAYS_MAX + "일이에요");
        }
        return from.plus(Duration.ofDays(days));
    }

    private AdminNoticeDtos.Item item(Notice n) {
        Integer days = n.getPopupUntil() == null ? null
                : (int) Math.max(1, Math.round(Duration.between(n.getCreatedAt(), n.getPopupUntil()).toMinutes() / 1440.0)); // 저장 시각 차이(밀리초)는 반올림
        return new AdminNoticeDtos.Item(n.getId(), n.getTitle(), n.getBody(), n.isPinned(), n.isPopupEnabled(), n.getPopupUntil(), days,
                n.getCreatedAt(), n.getUpdatedAt(),
                n.getUpdatedAt().isAfter(n.getCreatedAt().plus(EDIT_GRACE)));
    }
}
