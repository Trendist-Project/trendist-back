package com.trendist.user.service;

import com.trendist.common.exception.ApiException;
import com.trendist.user.entity.Sanction;
import com.trendist.user.entity.SanctionType;
import com.trendist.user.repository.SanctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SanctionServiceImpl implements SanctionService {
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    private final SanctionRepository sanctions;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Instant activeUntil(long userId, String type) {
        return sanctions.activeUntil(userId, type, Instant.now(clock));
    }

    /** 채팅은 메시지마다 확인하므로 짧게 캐시한다(값이 없으면 Instant.EPOCH) */
    private final com.github.benmanes.caffeine.cache.Cache<Long, Instant> chatBans = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(java.time.Duration.ofSeconds(15)).maximumSize(50_000).build();

    @Override
    @Transactional(readOnly = true)
    public Instant chatBannedUntil(long playerId) {
        Instant now = Instant.now(clock);
        Instant until = chatBans.get(playerId, id -> {
            Instant u = sanctions.activeUntilForPlayer(id, SanctionType.CHAT, now);
            return u == null ? Instant.EPOCH : u;
        });
        return until.isAfter(now) ? until : null;
    }

    @Override
    public void evictCache() { chatBans.invalidateAll(); }

    @Override
    public String untilText(Instant until) {
        return until.equals(Sanction.FOREVER) ? "영구" : UNTIL.format(until) + "까지";
    }

    private static final DateTimeFormatter UNTIL_EN = DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'", java.util.Locale.ENGLISH).withZone(ZoneId.of("UTC"));

    @Override
    public String untilText(Instant until, com.trendist.common.support.Lang lang) {
        if (!lang.isEn()) return untilText(until);
        return until.equals(Sanction.FOREVER) ? "permanent" : "until " + UNTIL_EN.format(until);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCanComment(long userId) {
        Instant until = activeUntil(userId, SanctionType.COMMENT);
        if (until == null) return;
        throw ApiException.forbiddenCode("COMMENT_BANNED", until.equals(Sanction.FOREVER) ? "댓글 작성이 정지된 계정이에요"
                : "댓글 작성이 정지된 계정이에요(" + UNTIL.format(until) + "까지)");
    }
}
