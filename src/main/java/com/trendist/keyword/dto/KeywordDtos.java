package com.trendist.keyword.dto;

import com.trendist.player.dto.PlayerDtos;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class KeywordDtos {
    private KeywordDtos() {}

    /** 결과 카드(4.4). id가 null이면 아직 DB에 없는 새 단어(💤 카드). */
    public record KeywordCard(Long id, String text, String state, BigDecimal trendValue,
                              int holderCount, long totalPlusCount, long searchCount,
                              boolean plusedByMe, Instant myExpiresAt) {}

    public record SearchResult(String status, KeywordCard keyword) {}

    public record MyKeyword(long keywordId, String text, String state, BigDecimal trendValue,
                            Instant plusedAt, Instant expiresAt) {}

    /** 구슬을 먹은 결과(4.20). status: GAINED(새로 추가) / DUPLICATE(이미 보유) / MISS(꽝) / NONE(🔥 키워드 없음). */
    public record OrbResult(String status, String text, String state, BigDecimal trendValue, int holderCount,
                            BigDecimal gained, Instant expiresAt) {}

    public record PlusResult(KeywordCard keyword, BigDecimal gained, PlayerDtos.PlayerSummary player) {}

    /**
     * 활동 그래프의 점 하나(4.12, 9.9). t는 그 구간의 시작(24시간은 정시, 나머지는 나라 현지 자정).
     * search·plus는 그 구간의 증분, holders는 구간이 끝날 때 가진 사람 수. live면 아직 집계 중인 이번 시간·오늘.
     */
    public record ActivityPoint(Instant t, long search, long plus, int holders, boolean live) {}

    /** 활동 그래프. range: 24h / 7d / 30d / 180d, timezone: 나라 시간대. */
    public record Activity(String range, String timezone, List<ActivityPoint> points, long totalSearch, long totalPlus, int holders) {}

    public record PlusRequest(@jakarta.validation.constraints.NotBlank String text) {}
}
