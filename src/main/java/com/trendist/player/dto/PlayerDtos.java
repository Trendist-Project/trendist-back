package com.trendist.player.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class PlayerDtos {
    private PlayerDtos() {}

    public record PlayerSummary(long id, String nickname, String countryCode, int level, long exp, long nextLevelExp,
                                BigDecimal currentScore, int holdCount, int totalPlusCount, int commentScore,
                                Instant nicknameChangeableAt) {}

    public record Me(long userId, boolean guest, PlayerSummary player) {}

    public record CreatePlayerRequest(@jakarta.validation.constraints.NotBlank String nickname, String countryCode) {}

    public record ChangeNicknameRequest(@jakarta.validation.constraints.NotBlank String nickname) {}
}
