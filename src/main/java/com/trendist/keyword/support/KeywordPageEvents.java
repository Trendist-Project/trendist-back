package com.trendist.keyword.support;

/** 공개 키워드 페이지(4.28)에 영향을 주는 변경 알림. 금지·해제가 커밋된 뒤 페이지·사이트맵 캐시를 바로 비운다. */
public final class KeywordPageEvents {
    private KeywordPageEvents() {}

    /** countryCode는 나라 코드(KR·GLOBAL), normalized는 정규화 키 */
    public record Changed(String countryCode, String normalized) {}

    /** 키워드가 금지됐다(커밋 뒤 모든 플레이어의 보관함에서 그 키워드와, 금지어가 들어 있는 키워드를 지운다, 4.7) */
    public record Banned(String countryCode, String normalized) {}
}
