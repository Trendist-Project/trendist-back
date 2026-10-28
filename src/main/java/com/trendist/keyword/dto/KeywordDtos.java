package com.trendist.keyword.dto;

import com.trendist.player.dto.PlayerDtos;
import java.math.BigDecimal;
import java.time.Instant;

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

    public record PlusRequest(@jakarta.validation.constraints.NotBlank String text) {}
}
