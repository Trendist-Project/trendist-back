package com.trendist.auth.service;

import java.util.Optional;

/** 액세스 토큰(JWT). sub = users.id, 수명 15분(9.3). */
public interface JwtService {
    String issue(long userId);

    long ttlSeconds();

    /** 서명과 만료를 검사하고 사용자 번호를 돌려준다. */
    Optional<Long> verify(String token);
}
