package com.trendist.emergency.service;

import com.trendist.common.exception.ApiException;
import com.trendist.emergency.dto.EmergencyDtos;
import com.trendist.emergency.entity.EmergencyNotice;
import com.trendist.emergency.event.EmergencyEvents;
import com.trendist.emergency.repository.EmergencyNoticeRepository;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmergencyServiceImpl implements EmergencyService {
    private static final Pattern WS = Pattern.compile("\\s+");
    /** 줄바꿈·제어 문자는 공백으로 바꾼다(한 줄로 흘러가야 하므로) */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    private final EmergencyNoticeRepository notices;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<EmergencyDtos.Active> active() {
        Instant now = Instant.now(clock);
        return notices.findFirstByCanceledAtIsNullAndEndsAtAfterOrderByIdDesc(now).map(n -> active(n, now));
    }

    @Override
    @Transactional
    public EmergencyDtos.Item create(long authorUserId, EmergencyDtos.Request req) {
        String message = line(req.message(), true, "안내 내용을 입력해 주세요");
        String messageEn = line(req.messageEn(), false, null);
        int minutes = req.minutes() == null ? 0 : req.minutes();
        if (minutes < 1 || minutes > MAX_MINUTES) throw ApiException.badRequest("EMERGENCY_MINUTES", "유지 시간은 1분에서 " + MAX_MINUTES + "분 사이로 정해 주세요");
        User author = users.findById(authorUserId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        Instant now = Instant.now(clock);
        notices.findByCanceledAtIsNullAndEndsAtAfter(now).forEach(n -> n.cancel(now)); // 한 번에 하나만 보인다
        EmergencyNotice n = notices.saveAndFlush(new EmergencyNotice(author, message, messageEn, now.plus(Duration.ofMinutes(minutes))));
        events.publishEvent(new EmergencyEvents.Changed(active(n, now)));
        return item(n, now);
    }

    @Override
    @Transactional
    public EmergencyDtos.Item cancel(long id) {
        EmergencyNotice n = notices.findById(id).orElseThrow(() -> ApiException.notFound("EMERGENCY_NOT_FOUND", "없는 긴급 안내예요"));
        Instant now = Instant.now(clock);
        boolean wasActive = n.isActive(now);
        n.cancel(now);
        notices.flush();
        if (wasActive) events.publishEvent(new EmergencyEvents.Changed(null)); // 보이던 안내를 끝냈으니 모두에게서 걷는다
        return item(n, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyDtos.Item> adminList() {
        Instant now = Instant.now(clock);
        return notices.findTop20ByOrderByIdDesc().stream().map(n -> item(n, now)).toList();
    }

    /** 공백·줄바꿈을 한 칸으로 줄이고 앞뒤를 다듬는다. required이면 비었을 때 오류, 너무 길어도 오류 */
    private static String line(String raw, boolean required, String emptyMessage) {
        String s = raw == null ? "" : WS.matcher(CONTROL.matcher(raw).replaceAll(" ")).replaceAll(" ").strip();
        if (s.isEmpty()) {
            if (required) throw ApiException.badRequest("EMERGENCY_EMPTY", emptyMessage);
            return null;
        }
        if (s.codePointCount(0, s.length()) > MAX_LENGTH) throw ApiException.badRequest("EMERGENCY_LENGTH", "안내는 " + MAX_LENGTH + "자까지 쓸 수 있어요");
        return s;
    }

    private static EmergencyDtos.Active active(EmergencyNotice n, Instant now) {
        return new EmergencyDtos.Active(n.getId(), n.getMessage(), n.getMessageEn(), n.getEndsAt(), Math.max(0, Duration.between(now, n.getEndsAt()).toSeconds()));
    }

    private static EmergencyDtos.Item item(EmergencyNotice n, Instant now) {
        String status = n.getCanceledAt() != null ? "CANCELED" : n.getEndsAt().isAfter(now) ? "ACTIVE" : "ENDED";
        return new EmergencyDtos.Item(n.getId(), n.getMessage(), n.getMessageEn(), n.getCreatedAt(), n.getEndsAt(), n.getCanceledAt(), status,
                Math.max(1, Duration.between(n.getCreatedAt(), n.getEndsAt()).toMinutes()));
    }
}
