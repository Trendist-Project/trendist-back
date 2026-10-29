package com.trendist.auth.service;

import com.trendist.auth.dto.TokenResponse;
import java.util.Optional;

/**
 * 인증(9.3). 게스트는 방문자 ID, 계정은 구글 ID 토큰을 서버가 검증한 뒤 이 사이트의 JWT로 인증한다.
 * 액세스 토큰(JWT, 15분)은 브라우저 메모리에, 갱신 토큰(1일, 회전)은 HttpOnly 쿠키에 둔다. 쿠키는 컨트롤러가 다룬다.
 */
public interface AuthService {

    /** issuedVisitorId는 새 게스트를 만들었을 때만 값이 있다(쿠키로 내려 줄 값). */
    record GuestStart(TokenResponse token, String issuedVisitorId) {}

    /** [시작]: 유효한 방문자 ID의 게스트가 있으면 그 게스트를, 없으면 새 게스트를 만든다. */
    GuestStart startGuest(Optional<String> visitorId);

    /** 방문자 ID로 게스트를 찾아 토큰을 다시 발급한다. 구글이 연결되지 않은 정상 게스트만 받는다. */
    Optional<TokenResponse> resumeGuest(String visitorId);

    /** 구글 로그인 결과: 사이트 JWT, 새 갱신 토큰(쿠키로 내려 줄 원본), 무엇을 했는지(LINKED 게스트 기록에 연결 / EXISTING 기존 계정 / CREATED 새 계정). */
    record Login(TokenResponse token, String refreshToken, String result) {}

    /**
     * 구글 ID 토큰을 검증하고 로그인한다. 처음 보는 구글 계정이면 지금 게스트(방문자 ID)에 OAuth 값만 채워 기록을 이어 가고,
     * 게스트가 없으면 OAuth 값만 가진 새 계정을 만든다. 이미 연결된 구글 계정이면 그 계정으로 들어간다(게스트 기록은 합치지 않는다).
     * nonce를 주면 토큰 안의 nonce와 같아야 한다(가로챈 토큰을 다시 쓰지 못하게).
     */
    Login google(String idToken, String nonce, Optional<String> visitorId);

    /** 갱신 토큰으로 새 액세스 토큰과 새 갱신 토큰을 발급한다(회전). 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 사용자의 토큰을 모두 폐기한다. */
    Optional<Login> refresh(String refreshToken);

    /**
     * 로그아웃: 그 갱신 토큰을 폐기하고, 그 사용자에게 지금까지 발급한 액세스 토큰(JWT)도 남은 수명과 관계없이 무효로 한다.
     * 사용자는 갱신 토큰 쿠키나(있으면) 요청의 액세스 토큰으로 찾는다.
     */
    void logout(Optional<String> refreshToken, Optional<Long> userId);
}
