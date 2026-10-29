package com.trendist.setting.service;

/** settings 테이블의 밸런스 값(13.3). 1분 캐시로 읽고, 행이 없으면 코드의 기본값을 쓴다. */
public interface SettingService {
    double getDouble(String name);

    int getInt(String name);

    void invalidate();

    /** 관리자 화면의 설정 한 줄(13.3). defaultValue는 코드의 기본값. */
    record Item(String name, String value, String type, java.math.BigDecimal min, java.math.BigDecimal max, String description,
                String defaultValue, java.time.Instant updatedAt) {}

    /** 모든 설정(코드의 기본값 순서대로). */
    java.util.List<Item> list();

    /** 값을 바꾼다. 숫자 형식·정수 여부·허용 범위를 검사하고, 저장하면 캐시를 비워 바로(방에는 1초 안에) 반영한다. */
    Item update(long adminUserId, String name, String value);
}
