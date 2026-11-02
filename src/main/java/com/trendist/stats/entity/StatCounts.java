package com.trendist.stats.entity;

/** 시간별·하루별 통계 행이 같이 가진 활동 수(ActivityCounter.Kind 순서). */
public interface StatCounts {
    long getNewPlayers();
    long getPlusCount();
    long getSearchCount();
    long getOrbCount();
    long getCollisionCount();
    long getChatCount();
    long getCommentCount();
    long getShareCount();

    default long[] counts() {
        return new long[] {getNewPlayers(), getPlusCount(), getSearchCount(), getOrbCount(), getCollisionCount(),
                getChatCount(), getCommentCount(), getShareCount()};
    }
}
