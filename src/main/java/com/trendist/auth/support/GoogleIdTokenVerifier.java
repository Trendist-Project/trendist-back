package com.trendist.auth.support;

import java.util.Optional;

/** 브라우저가 구글에서 받은 ID 토큰을 검증해 구글 사용자 ID(sub)와 nonce를 꺼낸다(9.3). 테스트에서는 가짜로 바꿔 끼운다. */
public interface GoogleIdTokenVerifier {
    /** 검증된 구글 사용자. nonce는 로그인 요청 때 브라우저가 만든 일회용 값(토큰을 다시 쓰지 못하게 비교한다). */
    record GoogleIdentity(String sub, String nonce) {}

    Optional<GoogleIdentity> verify(String idToken);
}
