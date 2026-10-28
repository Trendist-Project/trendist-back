package com.trendist.auth.controller;

import com.trendist.auth.dto.TokenResponse;
import com.trendist.auth.service.AuthService;
import com.trendist.auth.support.VisitorIds;
import com.trendist.common.exception.ApiException;
import com.trendist.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 게스트 시작과 세션 이어 가기(9.3). 방문자 ID 쿠키는 여기서만 읽고 쓴다. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    static final String VISITOR_COOKIE = "visitor_id";

    private final AuthService auth;
    private final AppProperties props;

    /** [시작]: 유효한 방문자 ID 쿠키가 있으면 그 게스트를, 없으면 새 게스트를 만든다. */
    @PostMapping("/guest")
    public TokenResponse guest(HttpServletRequest req, HttpServletResponse res) {
        AuthService.GuestStart start = auth.startGuest(readVisitorId(req));
        if (start.issuedVisitorId() != null) writeVisitorCookie(res, start.issuedVisitorId(), Duration.ofDays(365));
        return start.token();
    }

    /** 토큰이 없거나 만료되면 방문자 ID로 게스트를 찾아 다시 발급한다. 찾지 못하면 쿠키를 지우고 401. */
    @PostMapping("/session")
    public TokenResponse session(HttpServletRequest req, HttpServletResponse res) {
        Optional<String> vid = readVisitorId(req);
        if (vid.isEmpty()) {
            if (hasVisitorCookie(req)) writeVisitorCookie(res, "", Duration.ZERO); // 형식 오류 쿠키는 지운다
            throw ApiException.unauthorized("NO_SESSION");
        }
        return auth.resumeGuest(vid.get()).orElseThrow(() -> {
            writeVisitorCookie(res, "", Duration.ZERO);
            return ApiException.unauthorized("NO_SESSION");
        });
    }

    private Optional<String> readVisitorId(HttpServletRequest req) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies()).filter(c -> VISITOR_COOKIE.equals(c.getName()))
                .map(Cookie::getValue).filter(VisitorIds::isValid).findFirst();
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
