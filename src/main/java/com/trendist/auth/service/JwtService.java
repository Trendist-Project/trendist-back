package com.trendist.auth.service;

import java.util.Optional;

/** 액세스 토큰(JWT). sub = users.id, iat = 발급 시각, 수명 15분(9.3). */
public interface JwtService {
    String issue(long userId);

    long ttlSeconds();

    /** 서명과 만료를 검사하고 사용자 번호와 발급 시각을 돌려준다(로그아웃 전에 발급한 토큰인지는 AccountGuard가 본다). */
    /** 검증된 토큰: 사용자 번호와 발급 시각(초). */
    record Verified(long userId, long issuedAt) {}

    Optional<Verified> verify(String token);
}
