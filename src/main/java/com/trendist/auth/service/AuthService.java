package com.trendist.auth.service;

import com.trendist.auth.dto.TokenResponse;
import java.util.Optional;

/** 게스트 시작과 세션 이어 가기(9.3). 구글 로그인·갱신 토큰은 다음 단계에서 붙인다. 쿠키는 컨트롤러가 다룬다. */
public interface AuthService {

    /** issuedVisitorId는 새 게스트를 만들었을 때만 값이 있다(쿠키로 내려 줄 값). */
    record GuestStart(TokenResponse token, String issuedVisitorId) {}

    /** [시작]: 유효한 방문자 ID의 게스트가 있으면 그 게스트를, 없으면 새 게스트를 만든다. */
    GuestStart startGuest(Optional<String> visitorId);

    /** 방문자 ID로 게스트를 찾아 토큰을 다시 발급한다. 구글이 연결되지 않은 정상 게스트만 받는다. */
    Optional<TokenResponse> resumeGuest(String visitorId);
}
