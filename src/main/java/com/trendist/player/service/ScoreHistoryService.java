package com.trendist.player.service;

import com.trendist.player.dto.PlayerDtos;
import java.time.Instant;

/** 플레이어 점수 추이(4.15): 매시 기록과 그래프용 조회. */
public interface ScoreHistoryService {
    /**
     * 끝난 한 시간을 기록한다(매시 정각 잡). 그 시간에 점수가 바뀐 플레이어만, 그 나라 현지 시 칸을 덮어쓰고
     * 그 현지 날짜의 하루 행을 그 값(그날 마지막 값)으로 맞춘다. 기록한 수를 돌려준다.
     *
     * @param hourStart 끝난 시간의 시작(UTC 정시)
     */
    int recordHour(Instant hourStart);

    /** 내 점수 추이. range: 24h(시간 단위) / 7d / 30d / all(하루 단위). 기록이 없는 칸은 직전 값을 이어 쓴다. */
    PlayerDtos.ScoreHistory history(long userId, String range);

    /** 같은 방 다른 플레이어의 최근 7일 추이(정보 팝업, 4.11·4.15). 다른 사람은 7일만 볼 수 있다. */
    PlayerDtos.ScoreHistory weekOf(long playerId);
}
