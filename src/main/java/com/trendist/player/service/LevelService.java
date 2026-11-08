package com.trendist.player.service;

import com.trendist.player.entity.Level;

/** levels 표로 EXP에서 레벨을 정한다(4.9). 관리자가 바꾸면 1분 안에 반영된다. */
public interface LevelService {
    int levelFor(long exp);

    /** EXP에 맞는 레벨표 행(players.level_id에 넣는다) */
    Level rowFor(long exp);

    /** 레벨 숫자의 레벨표 행. 표에 없는 레벨이면 예외 */
    Level row(long level);

    /** 레벨 표시 옆 성장 아이콘의 바탕(4.9). 1~100레벨 순서대로 */
    java.util.List<Badge> badges();

    /** color: #RRGGBB 또는 null(기본 거북이 색), alpha: 진하기 0~100(%), rainbow: true면 무지개 그라데이션 */
    record Badge(int level, String color, int alpha, boolean rainbow) {}

    /** 레벨표 전체(1~100레벨 순서대로). 가이드가 레벨별 필요 EXP를 공개하는 데 쓴다 */
    java.util.List<Level> all();

    /** 다음 레벨에 필요한 누적 EXP. 최고 레벨이면 -1. */
    long nextRequired(long level);
}
