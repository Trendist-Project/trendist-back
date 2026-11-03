package com.trendist.keyword.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 구글 제미나이로 키워드 한 줄 설명을 받는다(4.12, 10.4). 테스트에서는 가짜로 바꿔 끼운다. */
public interface GeminiClient {
    /** API 키가 설정돼 있어 부를 수 있으면 true */
    boolean enabled();

    /**
     * 키워드 여러 개의 한 줄 설명을 요청 한 번으로 받는다(요청 수·토큰·시간을 줄인다). 유행어면 뜻, 이슈면 무슨 내용인지.
     * 돌려주는 맵의 키는 입력 키워드 글자 그대로이고, 값이 빈 것은 모르거나 쓸 수 없는 답(UNKNOWN 등), 키가 없는 것은 답을 받지 못한 것이다.
     * grounding이면 구글 검색으로 확인한다(무료 등급에서는 막혀 있다). 한도 초과면 {@link RateLimitedException}, 그 밖의 실패는 예외.
     */
    Map<String, Optional<String>> describeAll(List<String> keywords, boolean grounding) throws Exception;

    /** 제미나이의 요청 한도(429)에 걸렸다. 한동안 부르지 않는다. */
    class RateLimitedException extends RuntimeException {
        public RateLimitedException(String message) { super(message); }
    }
}
