package com.trendist.auth;

import com.trendist.common.ApiException;
import com.trendist.common.CodeTables;
import com.trendist.config.AppProperties;
import com.trendist.domain.User;
import com.trendist.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 게스트 시작과 세션 이어 가기(9.3). 구글 로그인·갱신 토큰은 다음 단계에서 붙인다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String VISITOR_COOKIE = "tv_id";

    private final UserRepository users;
    private final CodeTables codes;
    private final JwtService jwt;
    private final AccountGuard guard;
    private final AppProperties props;
    private final Clock clock;

    public AuthController(UserRepository users, CodeTables codes, JwtService jwt, AccountGuard guard,
                          AppProperties props, Clock clock) {
        this.users = users; this.codes = codes; this.jwt = jwt; this.guard = guard; this.props = props; this.clock = clock;
    }

    public record TokenResponse(String accessToken, long expiresIn) {}

    /** [시작]: 유효한 방문자 ID 쿠키가 있으면 그 게스트를, 없으면 새 게스트를 만든다. */
    @PostMapping("/guest")
    @Transactional
    public TokenResponse guest(HttpServletRequest req, HttpServletResponse res) {
        Optional<User> existing = readVisitorId(req).flatMap(users::findByVisitorId).filter(this::isGuestUsable);
        User user = existing.orElseGet(() -> {
            String vid = VisitorIds.create(clock);
            User u = users.save(User.guest(vid, codes.userState(CodeTables.USER_NORMAL)));
            writeVisitorCookie(res, vid, Duration.ofDays(365));
            return u;
        });
        return new TokenResponse(jwt.issue(user.getId()), jwt.ttlSeconds());
    }

    /** 토큰이 없거나 만료되면 방문자 ID로 게스트를 찾아 다시 발급한다. 찾지 못하면 401. */
    @PostMapping("/session")
    @Transactional(readOnly = true)
    public TokenResponse session(HttpServletRequest req, HttpServletResponse res) {
        Optional<String> vid = readVisitorId(req);
        if (vid.isEmpty()) {
            if (hasVisitorCookie(req)) writeVisitorCookie(res, "", Duration.ZERO); // 형식 오류 쿠키는 지운다
            throw ApiException.unauthorized("NO_SESSION");
        }
        User user = users.findByVisitorId(vid.get()).filter(this::isGuestUsable)
                .orElseThrow(() -> {
                    writeVisitorCookie(res, "", Duration.ZERO);
                    return ApiException.unauthorized("NO_SESSION");
                });
        return new TokenResponse(jwt.issue(user.getId()), jwt.ttlSeconds());
    }

    /** 방문자 ID로는 구글이 연결되지 않은, 정상 상태의 게스트만 토큰을 받는다. */
    private boolean isGuestUsable(User u) {
        return u.getOauthId() == null && guard.isUsable(u.getId());
    }

    private Optional<String> readVisitorId(HttpServletRequest req) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies()).filter(c -> VISITOR_COOKIE.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).filter(VisitorIds::isValid).findFirst();
    }

    private boolean hasVisitorCookie(HttpServletRequest req) {
        return req.getCookies() != null && Arrays.stream(req.getCookies()).anyMatch(c -> VISITOR_COOKIE.equals(c.getName()));
    }

    private void writeVisitorCookie(HttpServletResponse res, String value, Duration maxAge) {
        ResponseCookie c = ResponseCookie.from(VISITOR_COOKIE, value)
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Lax").path("/").maxAge(maxAge).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }
}
