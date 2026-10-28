package com.trendist.player;

import java.math.BigDecimal;

public final class PlayerDtos {
    private PlayerDtos() {}

    public record PlayerSummary(long id, String nickname, String countryCode, int level, long exp, long nextLevelExp,
                                BigDecimal currentScore, int holdCount, int totalPlusCount, int commentScore) {}

    public record Me(long userId, boolean guest, PlayerSummary player) {}

    public record CreatePlayerRequest(@jakarta.validation.constraints.NotBlank String nickname, String countryCode) {}

    public record RankingEntry(int rank, long playerId, String nickname, int level, BigDecimal score) {}
}
