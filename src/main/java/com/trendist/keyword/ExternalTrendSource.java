package com.trendist.keyword;

import java.util.OptionalDouble;

/** 외부 소스 조회(10.2). 값(0~100)이 없으면 외부 데이터 없음. */
public interface ExternalTrendSource {
    /** 이 나라에서 이 소스가 쓰이는가. */
    boolean supports(String countryCode);

    /** data_sources.name */
    String sourceName();

    OptionalDouble heat(String countryCode, String normalized);
}
