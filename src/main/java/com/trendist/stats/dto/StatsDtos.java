package com.trendist.stats.dto;

import java.util.List;
import java.util.Map;

/** 관리자 통계(13.9). */
public final class StatsDtos {
    private StatsDtos() {}

    /** 한 기간(오늘·어제)의 값. activePlayers는 그날 한 번이라도 방에 있던 사람(DAU). */
    public record Period(long peakOnline, long activePlayers, long newPlayers, long plus, long search, long orb,
                         long collision, long chat, long comment, long share) {}

    /** 사용자 수. players는 이 나라 플레이어, wau·mau는 최근 7일·30일 안에 방에 들어온 플레이어 */
    public record Totals(long users, long guests, long google, long admins, long withdrawn, long banned,
                         long players, long wau, long mau) {}

    public record Bucket(String label, long count) {}

    public record Summary(long onlineNow, Period today, Period yesterday, Totals totals,
                          Map<String, Long> keywordStates, long pendingReports, List<Bucket> levels) {}

    /** 그래프의 한 점. t는 시간별이면 그 시간의 시작(UTC), 하루별이면 현지 날짜. live면 아직 집계 중. */
    public record Row(String t, long peakOnline, double avgOnline, long activePlayers, long newPlayers, long plus,
                      long search, long orb, long collision, long chat, long comment, long share, boolean live) {}
}
