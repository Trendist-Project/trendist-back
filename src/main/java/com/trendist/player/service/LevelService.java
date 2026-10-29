package com.trendist.player.service;

/** levels 표로 EXP에서 레벨을 정한다(4.9). 관리자가 바꾸면 1분 안에 반영된다. */
public interface LevelService {
    int levelFor(long exp);

    /** 다음 레벨에 필요한 누적 EXP. 최고 레벨이면 -1. */
    long nextRequired(long level);
}
