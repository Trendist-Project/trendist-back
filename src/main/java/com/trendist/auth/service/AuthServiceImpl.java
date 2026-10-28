package com.trendist.auth.service;

import com.trendist.auth.dto.TokenResponse;
import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.support.VisitorIds;
import com.trendist.common.code.CodeTables;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final CodeTables codes;
    private final JwtService jwt;
    private final AccountGuard guard;
    private final Clock clock;

    @Override
    @Transactional
    public GuestStart startGuest(Optional<String> visitorId) {
        Optional<User> existing = visitorId.flatMap(users::findByVisitorId).filter(this::isGuestUsable);
        if (existing.isPresent()) return new GuestStart(token(existing.get()), null);
        String vid = VisitorIds.create(clock);
        User u = users.save(User.guest(vid, codes.userState(CodeTables.USER_NORMAL)));
        return new GuestStart(token(u), vid);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TokenResponse> resumeGuest(String visitorId) {
        return users.findByVisitorId(visitorId).filter(this::isGuestUsable).map(this::token);
    }

    /** 방문자 ID로는 구글이 연결되지 않은, 정상 상태의 게스트만 토큰을 받는다. */
    private boolean isGuestUsable(User u) {
        return u.getOauthId() == null && guard.isUsable(u.getId());
    }

    private TokenResponse token(User u) {
        return new TokenResponse(jwt.issue(u.getId()), jwt.ttlSeconds());
    }
}
