package com.trendist.player.service;

import com.trendist.player.entity.Level;

/**
 * levels 표로 레벨을 정한다(4.9). 관리자가 바꾸면 1분 안에 반영된다.
 * EXP는 누적하지 않는다: `levels.required_exp`는 그 레벨이 되는 데 필요한 EXP(바로 앞 레벨에서 올라올 때, 1레벨은 0)이고,
 * 플레이어의 `exp`는 지금 레벨에서 모은 EXP다. 레벨이 오르면 필요한 만큼 빼고 남은 EXP를 이어 갖는다({@link #advance}).
 */
public interface LevelService {
    /** 지금까지 모은 EXP 전체(누적)로 본 레벨. 봇처럼 레벨을 따로 저장하지 않는 쪽이 쓴다 */
    int levelFor(long totalExp);

    /** 누적 EXP에 맞는 레벨표 행. 1레벨(새 플레이어는 rowFor(0))부터 시작할 때 쓴다 */
    Level rowFor(long totalExp);

    /** 레벨 + 그 레벨에서 모은 EXP를 올릴 수 있는 만큼 올린 결과(남은 EXP는 이어 가진다). 최고 레벨이면 EXP만 쌓인다 */
    Progress advance(long level, long exp);

    record Progress(Level row, long exp) {}

    /** 그 레벨까지 올라오는 데 모은 EXP 전체(레벨표의 1~레벨 합). 스킬 해금 안내처럼 "총 얼마"를 보일 때 쓴다 */
    long cumulative(long level);

    /** 레벨과 그 레벨에서 모은 EXP로 본 누적 EXP(EXP 점수 반영 상한 계산에 쓴다) */
    default long totalExp(long level, long exp) { return cumulative(level) + exp; }

    /** 레벨 숫자의 레벨표 행. 표에 없는 레벨이면 예외 */
    Level row(long level);

    /** 레벨 표시 옆 성장 아이콘의 바탕(4.9). 1~100레벨 순서대로 */
    java.util.List<Badge> badges();

    /** color: #RRGGBB 또는 null(기본 거북이 색), alpha: 진하기 0~100(%), rainbow: true면 무지개 그라데이션 */
    record Badge(int level, String color, int alpha, boolean rainbow) {}

    /** 레벨표 전체(1~100레벨 순서대로). 가이드가 레벨별 필요 EXP를 공개하는 데 쓴다 */
    java.util.List<Level> all();

    /** 다음 레벨로 올라가는 데 필요한 EXP(지금 레벨에서 모아야 하는 양). 최고 레벨이면 -1. */
    long nextRequired(long level);
}
