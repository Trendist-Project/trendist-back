package com.trendist.stats.service;

import com.trendist.stats.dto.StatsDtos;
import java.util.List;

/** 관리자 통계(13.9): 접속 추이와 활동량. 표본은 10초마다, 기록은 매시 정각. */
public interface StatsService {
    /** 10초마다: 나라별 방 접속 인원 표본. 1분마다 접속 중인 플레이어의 마지막 접속 시각도 고친다. */
    void sample();

    /** 매시 정각: 지난 한 시간의 행을 쓰고, 그날 행을 다시 만든다. */
    void closeHour();

    /** 서버를 끌 때: 이번 시간에 모은 값을 저장한다(다시 켜면 같은 시간 행에 이어서 더한다). */
    void flushCurrentHour();

    StatsDtos.Summary summary(String countryCode);

    /** 최근 hours시간(이번 시간 포함), 오래된 순. 기록이 없는 시간은 0. */
    List<StatsDtos.Row> hourly(String countryCode, int hours);

    /** 최근 days일(오늘 포함), 오래된 순. */
    List<StatsDtos.Row> daily(String countryCode, int days);
}
