package com.trendist.player.service;

import com.trendist.player.entity.Level;

/** levels 표로 EXP에서 레벨을 정한다(4.9). 관리자가 바꾸면 1분 안에 반영된다. */
public interface LevelService {
    int levelFor(long exp);

    /** EXP에 맞는 레벨표 행(players.level_id에 넣는다) */
    Level rowFor(long exp);

    /** 레벨 숫자의 레벨표 행. 표에 없는 레벨이면 예외 */
    Level row(long level);

    /** 다음 레벨에 필요한 누적 EXP. 최고 레벨이면 -1. */
    long nextRequired(long level);
}
