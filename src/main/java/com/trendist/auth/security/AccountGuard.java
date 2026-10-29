package com.trendist.auth.security;

import com.trendist.auth.service.JwtService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.code.CodeTables;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 토큰이 살아 있는 동안에도 금지·탈퇴 계정과 로그아웃 전에 발급한 토큰을 막으려고 짧은 캐시로 상태를 확인한다(9.3). */
@Component
@RequiredArgsConstructor
public class AccountGuard {
    private final UserRepository users;
    private final CodeTables codes;
    private final Clock clock;
    /** 사용자 상태 캐시: 쓸 수 있는지와 로그아웃 시각(초, 없으면 Long.MIN_VALUE). */
    private record State(boolean usable, long revokedAt) {}
    private final Cache<Long, State> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(100_000).build();

    private State state(long userId) {
        return cache.get(userId, id -> users.findStatus(id)
                .map(s -> new State((s.bannedUntil() == null || !s.bannedUntil().isAfter(Instant.now(clock)))
                        && s.userStateId().equals(codes.userState(CodeTables.USER_NORMAL).getId()),
                        s.tokensRevokedAt() == null ? Long.MIN_VALUE : s.tokensRevokedAt().getEpochSecond()))
                .orElse(new State(false, Long.MIN_VALUE)));
    }

    public boolean isUsable(long userId) { return state(userId).usable(); }

    /** 이 액세스 토큰을 받아도 되는지: 계정을 쓸 수 있고, 로그아웃한 뒤에 발급한 토큰이어야 한다. */
    public boolean accepts(JwtService.Verified token) {
        State s = state(token.userId());
        return s.usable() && token.issuedAt() > s.revokedAt();
    }

    public void evict(long userId) { cache.invalidate(userId); }
}
