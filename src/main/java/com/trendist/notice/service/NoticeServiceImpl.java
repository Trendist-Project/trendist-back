package com.trendist.notice.service;

import com.trendist.notice.dto.NoticeItem;
import com.trendist.notice.repository.NoticeRepository;
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

    private final NoticeRepository notices;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<NoticeItem> list() {
        Instant now = Instant.now(clock);
        return notices.findByDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, LIMIT)).stream()
                .map(n -> new NoticeItem(n.getId(), n.getTitle(), n.getBody(), n.isPinned(), n.getCreatedAt(),
                        n.getUpdatedAt().isAfter(n.getCreatedAt().plus(EDIT_GRACE)),
                        n.getCreatedAt().isAfter(now.minus(FRESH))))
                .toList();
    }
}
