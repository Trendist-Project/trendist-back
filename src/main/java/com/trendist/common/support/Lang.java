package com.trendist.common.support;

import java.util.Locale;

/**
 * 응답 언어(ko 또는 en). 나라와 화면 언어는 묶여 있어(KR=ko, GLOBAL=en) 프론트가 Accept-Language: ko|en 으로 알려 준다.
 * 영어판 컬럼(*_en)이 비어 있으면 한국어로 대신한다. 브라우저가 보낸 en-US,en;q=0.9 같은 값도 읽는다.
 */
public enum Lang {
    KO, EN;

    /** Accept-Language 헤더 값에서 언어를 정한다. 없거나 영어가 아니면 한국어. 맨 앞 언어만 본다 */
    public static Lang parse(String acceptLanguage) {
        if (acceptLanguage == null) return KO;
        String first = acceptLanguage.strip().split("[,;]")[0].strip().toLowerCase(Locale.ROOT);
        return first.equals("en") || first.startsWith("en-") ? EN : KO;
    }

    /** 나라의 기본 로케일(countries.default_locale)로 정한다. 방 안 모두에게 같은 글을 보낼 때 쓴다 */
    public static Lang ofLocale(String locale) { return parse(locale); }

    public boolean isEn() { return this == EN; }

    /** "ko" 또는 "en" */
    public String code() { return name().toLowerCase(Locale.ROOT); }

    /** 이 언어의 값. 영어인데 영어 값이 비어 있으면 한국어 값 */
    public String pick(String ko, String en) { return this == EN && en != null && !en.isBlank() ? en : ko; }
}
