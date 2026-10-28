package com.trendist.setting.service;

/** settings 테이블의 밸런스 값(13.3). 1분 캐시로 읽고, 행이 없으면 코드의 기본값을 쓴다. */
public interface SettingService {
    double getDouble(String name);

    int getInt(String name);

    void invalidate();
}
