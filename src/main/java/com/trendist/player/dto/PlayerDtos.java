package com.trendist.player.dto;

import java.time.Instant;

public final class PlayerDtos {
    private PlayerDtos() {}

    public record PlayerSummary(long id, String nickname, String countryCode, long level, long exp, long nextLevelExp,
                                long currentScore, long holdCount, long totalPlusCount, long commentScore, long searchCount,
                                Instant nicknameChangeableAt) {}

    /** 점수 추이의 점 하나(4.15). t는 그 칸의 시작(24시간은 정시, 나머지는 나라 현지 자정), live면 지금 값. */
    public record ScorePoint(Instant t, long score, boolean live) {}

    /** 점수 추이. range: 24h / 7d / 30d / all, timezone: 나라 시간대. */
    public record ScoreHistory(String range, String timezone, java.util.List<ScorePoint> points) {}

    /** 정보 팝업에 보이는 키워드(4.11): 이름과 상태만. 점수·시각은 숨긴다. */
    public record KeywordTag(long keywordId, String text, String state) {}

    /**
     * 다른 플레이어 정보 팝업(4.11). 사용자 번호 대신 방 안에서만 쓰는 세션 엔티티 번호로 가리킨다(4.1).
     * title은 활성 칭호 이름(없으면 null), topKeywords는 보관함 중 점수 상위 3개.
     */
    public record PlayerCard(int entityNo, String nickname, boolean admin, String title, long level, long score,
                             long commentScore, java.util.List<KeywordTag> topKeywords) {}

    public record Me(long userId, boolean guest, PlayerSummary player) {}

    public record CreatePlayerRequest(@jakarta.validation.constraints.NotBlank String nickname, String countryCode) {}

    public record ChangeNicknameRequest(@jakarta.validation.constraints.NotBlank String nickname) {}
}
