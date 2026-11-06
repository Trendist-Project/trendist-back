package com.trendist.country.service;

import com.trendist.country.entity.Country;

/** 나라(4.18). 지금 열린 나라는 KR과 GLOBAL이다. */
public interface CountryService {
    String DEFAULT_COUNTRY = "KR";

    /** 열린 나라를 코드로 찾는다. code가 null이면 KR. 없거나 닫힌 나라면 400. */
    Country requireOpen(String code);
}
