package com.trendist.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.trendist.auth.security.AuthUser;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
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

/** 게스트 시작, 구글 로그인, 세션 이어 가기, 로그아웃(9.3). 방문자 ID·갱신 토큰 쿠키는 여기서만 읽고 쓴다. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    static final String VISITOR_COOKIE = "visitor_id";
    static final String REFRESH_COOKIE = "refresh_token";
    static final String REFRESH_PATH = "/";
    /** 예전에 쓰던 갱신 토큰 쿠키 경로. 개발자 도구 목록에 안 보이고 쿠키 지우기로도 안 지워져서 "/"로 옮겼다. 남은 것은 지운다 */
    static final String LEGACY_REFRESH_PATH = "/api/auth";

    private final AuthService auth;
    private final AppProperties props;

    /** [시작]: 유효한 방문자 ID 쿠키가 있으면 그 게스트를, 없으면 새 게스트를 만든다. */
    @PostMapping("/guest")
    public TokenResponse guest(HttpServletRequest req, HttpServletResponse res) {
        AuthService.GuestStart start = auth.startGuest(readVisitorId(req));
        if (start.issuedVisitorId() != null) writeVisitorCookie(res, start.issuedVisitorId(), Duration.ofDays(365));
        return start.token();
    }

    /**
     * 구글 로그인: 브라우저가 구글에서 받은 ID 토큰을 서버가 검증하고 이 사이트의 JWT(액세스 토큰)와 갱신 토큰 쿠키를 준다.
     * 게스트로 플레이하던 중이면 그 기록에 구글을 연결한다. 로그인하면 방문자 ID 쿠키는 지운다(이후는 갱신 토큰으로 이어 간다).
     */
    @PostMapping("/google")
    public GoogleLoginResponse google(@Valid @RequestBody GoogleLoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        AuthService.Login login = auth.google(body.idToken(), body.nonce(), readVisitorId(req));
        writeRefreshCookie(res, login.refreshToken(), Duration.ofDays(1));
        writeVisitorCookie(res, "", Duration.ZERO);
        return new GoogleLoginResponse(login.token().accessToken(), login.token().expiresIn(), login.result());
    }

    /** 로그아웃: 갱신 토큰을 폐기하고 쿠키를 지운다. 다시 [시작]하면 새 게스트로 시작한다. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthUser user, HttpServletRequest req, HttpServletResponse res) {
        auth.logout(readCookie(req, REFRESH_COOKIE), Optional.ofNullable(user).map(AuthUser::userId));
        writeRefreshCookie(res, "", Duration.ZERO);
    }

    /**
     * 액세스 토큰이 없거나 만료되면 다시 발급한다. 계정은 갱신 토큰 쿠키로(회전), 게스트는 방문자 ID로 이어 간다.
     * 둘 다 없거나 무효면 쿠키를 지우고 401(메인 화면).
     */
    @PostMapping("/session")
    public TokenResponse session(HttpServletRequest req, HttpServletResponse res) {
        Optional<String> refresh = readCookie(req, REFRESH_COOKIE);
        if (refresh.isPresent()) {
            Optional<AuthService.Login> login = auth.refresh(refresh.get());
            if (login.isPresent()) {
                writeRefreshCookie(res, login.get().refreshToken(), Duration.ofDays(1));
                return login.get().token();
            }
            writeRefreshCookie(res, "", Duration.ZERO); // 만료·폐기된 갱신 토큰
        }
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

    private Optional<String> readCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies()).filter(c -> name.equals(c.getName())).map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank()).findFirst();
    }

    private void writeRefreshCookie(HttpServletResponse res, String value, Duration maxAge) {
        ResponseCookie c = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Lax").path(REFRESH_PATH).maxAge(maxAge).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
        ResponseCookie legacy = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Lax").path(LEGACY_REFRESH_PATH).maxAge(Duration.ZERO).build();
        res.addHeader(HttpHeaders.SET_COOKIE, legacy.toString());
    }

    /** 구글 로그인 요청: 구글이 브라우저에 준 ID 토큰, 로그인을 시작할 때 브라우저가 만든 nonce(있으면 토큰과 비교). */
    public record GoogleLoginRequest(@jakarta.validation.constraints.NotBlank String idToken, String nonce) {}

    /** accessToken은 이 사이트의 JWT. result: LINKED(게스트 기록에 연결) / EXISTING(기존 계정) / CREATED(새 계정). */
    public record GoogleLoginResponse(String accessToken, long expiresIn, String result) {}

    private boolean hasVisitorCookie(HttpServletRequest req) {
        return req.getCookies() != null && Arrays.stream(req.getCookies()).anyMatch(c -> VISITOR_COOKIE.equals(c.getName()));
    }

    private void writeVisitorCookie(HttpServletResponse res, String value, Duration maxAge) {
        ResponseCookie c = ResponseCookie.from(VISITOR_COOKIE, value)
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Lax").path("/").maxAge(maxAge).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }
}
