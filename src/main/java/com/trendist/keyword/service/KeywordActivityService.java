package com.trendist.keyword.service;

import com.trendist.keyword.dto.KeywordDtos;
import java.time.Instant;
import java.util.Map;

/** 키워드 활동 기록과 활동 그래프(4.12, 9.9). */
public interface KeywordActivityService {
    /**
     * 끝난 한 시간을 기록한다(매시 정각 잡). 대상 키워드의 세 누적값과 그 시간의 증분으로 시간별 칸을 덮어쓰고,
     * 그 칸이 속한 현지 날짜의 하루 행을 다시 만든 뒤 180일이 지난 하루 행을 지운다.
     *
     * @param hourStart 끝난 시간의 시작(UTC 정시)
     * @param searched  그 시간에 DB로 옮긴 검색 수(키워드 번호 → 수)
     */
    void recordHour(Instant hourStart, Map<Long, Long> searched);

    /** 활동 그래프. 내 나라 키워드만, 금지 키워드는 볼 수 없다. range: 24h / 7d / 30d / 180d. */
    KeywordDtos.Activity activity(long userId, long keywordId, String range);
}
