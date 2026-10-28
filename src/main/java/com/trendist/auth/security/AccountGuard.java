package com.trendist.auth.security;

import com.trendist.common.code.CodeTables;
import com.trendist.user.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** 토큰이 살아 있는 동안에도 금지·탈퇴 계정을 막으려고 짧은 캐시로 상태를 확인한다(9.3). */
@Component
public class AccountGuard {
    private final UserRepository users;
    private final CodeTables codes;
    private final Clock clock;
    private final Cache<Long, Boolean> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(100_000).build();

    public AccountGuard(UserRepository users, CodeTables codes, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.clock = clock;
    }

    public boolean isUsable(long userId) {
        return cache.get(userId, id -> users.findStatus(id)
                .map(s -> (s.bannedUntil() == null || !s.bannedUntil().isAfter(Instant.now(clock)))
                        && s.userStateId().equals(codes.userState(CodeTables.USER_NORMAL).getId()))
                .orElse(false));
    }

    public void evict(long userId) { cache.invalidate(userId); }
}
