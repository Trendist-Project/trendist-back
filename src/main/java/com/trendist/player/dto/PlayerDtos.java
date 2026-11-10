package com.trendist.player.dto;

import java.time.Instant;

public final class PlayerDtos {
    private PlayerDtos() {}

    public record PlayerSummary(long id, String nickname, String countryCode, long level, long exp, long nextLevelExp,
                                long currentScore, long holdCount, long addCount, long commentScore, long searchCount,
                                Instant nicknameChangeableAt, long stealCount, long botWinCount, long activeDays, long killCount, long deathCount,
                                long popularity, Instant createdAt) {}

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
    public record PlayerCard(int entityNo, String nickname, String title, long level, long score,
                             long commentScore, java.util.List<KeywordTag> topKeywords,
                             long playerId, long popularity, int myVote, java.time.Instant createdAt, boolean me, OrbLook look, Stats stats) {}

    /**
     * 다른 플레이어 정보 창이 내 정보 창과 같은 구성으로 보이도록 함께 내려 주는 기록: 지금 레벨에서 모은 EXP와 다음 레벨 필요량(최고 레벨이면 -1),
     * 보유 키워드 수, 추가·검색·빼앗기·봇 사냥·처치·패배 횟수, 활동한 날. (키워드 이름은 상위 3개만 따로 공개한다)
     */
    public record Stats(long exp, long nextLevelExp, long holdCount, long addCount, long searchCount, long stealCount,
                        long botWinCount, long killCount, long deathCount, long activeDays) {}

    /**
     * 그 플레이어가 장착한 꾸미기 프로필의 원 모양(아레나에서 보이는 그대로): 바탕색, 테두리 색·두께(1~8단계), 픽셀 그림 주소, 테두리 효과 코드.
     * 검색·채팅에서 열어 방에 그 원이 없어도 정보 창에 꾸민 모양을 보여 주려고 카드에 함께 내려 준다. 값이 null이면 기본이다.
     */
    public record OrbLook(String bg, String borderColor, Integer borderWidth, String art, String borderEffect) {}

    /** 플레이어 검색 결과 한 줄(4.34). myVote: 오늘 내가 준 표(+1, -1, 없으면 0) */
    public record PlayerHit(long playerId, String nickname, long level, long score, long popularity, boolean me, int myVote) {}

    /** 인기도 투표 결과 */
    public record PopularityResult(long playerId, long popularity, int myVote) {}

    public record PopularityRequest(int value) {}

    public record Me(long userId, boolean guest, PlayerSummary player) {}

    public record CreatePlayerRequest(@jakarta.validation.constraints.NotBlank String nickname, String countryCode) {}

    public record ChangeNicknameRequest(@jakarta.validation.constraints.NotBlank String nickname) {}
}
