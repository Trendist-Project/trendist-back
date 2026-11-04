package com.trendist.keyword.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 구글 제미나이로 키워드 한 줄 설명을 받는다(4.12, 10.4). 테스트에서는 가짜로 바꿔 끼운다. */
public interface GeminiClient {
    /** API 키가 설정돼 있어 부를 수 있으면 true */
    boolean enabled();

    /**
     * 키워드 여러 개의 "왜 지금 유행인지" 한 줄을 요청 한 번으로 받는다(요청 수·토큰·시간을 줄인다).
     * news는 키워드별 관련 뉴스 제목(RSS가 함께 준다)이고, 있으면 이를 근거로 이유를 쓴다. 없는 키워드는 확실히 알 때만 쓴다.
     * 돌려주는 맵의 키는 입력 키워드 글자 그대로이고, 값이 빈 것은 모르거나 쓸 수 없는 답(UNKNOWN 등), 키가 없는 것은 답을 받지 못한 것이다.
     * lang은 설명 언어("ko"=한국어, "en"=영어: GLOBAL 방). 저장되는 설명이 그 언어로 쓰이게 프롬프트를 고른다.
     * grounding이면 구글 검색으로 확인한다(무료 등급에서는 막혀 있다). 한도 초과면 {@link RateLimitedException}, 그 밖의 실패는 예외.
     */
    Map<String, Optional<String>> describeAll(List<String> keywords, Map<String, List<String>> news, boolean grounding, String lang) throws Exception;

    /** 제미나이의 요청 한도(429)에 걸렸다. 한동안 부르지 않는다. */
    class RateLimitedException extends RuntimeException {
        public RateLimitedException(String message) { super(message); }
    }
}
