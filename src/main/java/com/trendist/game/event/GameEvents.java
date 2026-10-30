package com.trendist.game.event;

/** 트랜잭션이 끝난 뒤 방(Room)에 전달하는 이벤트(9.2 점수 실시간 반영). */
public final class GameEvents {
    private GameEvents() {}

    /** 한 플레이어의 점수를 DB 값으로 맞춘다(그 사람의 + / 잃기 / 만료 / 재합산 뒤). */
    public record PlayerScoreChanged(long countryId, long playerId, long score, long exp, long level, long holdCount) {}

    /** 키워드의 trend_value가 바뀌었다. 접속 중인 보유자 점수에 deltaScore를 더한다(excludePlayerId는 이미 정확한 값을 받음). */
    public record KeywordTrendChanged(long countryId, long keywordId, long deltaScore, Long excludePlayerId) {}

    /** 닉네임이 바뀌었다. 접속 중이면 방의 이름표를 바꾼다. */
    public record NicknameChanged(long countryId, long playerId, String nickname) {}

    /** 보유 색인 갱신(키워드 번호 → 플레이어). */
    public record HoldingChanged(long countryId, long playerId, long keywordId, boolean added) {}

    /** 칭호 조건 수치가 바뀌었을 수 있다. 커밋 뒤 METRIC 칭호를 검사한다(4.10). */
    public record TitleCheck(long playerId) {}

    /** 새 칭호를 받았다. 접속 중이면 그 사람에게 알린다(4.10). */
    public record TitleGranted(long countryId, long playerId, String name, String description) {}

    /** 활성 칭호가 바뀌었다(title이 null이면 숨김). 접속 중이면 방의 이름표 아래 칭호를 바꾼다. */
    public record TitleChanged(long countryId, long playerId, String title) {}

    /** 꾸미기가 바뀌었다(4.9). 접속 중이면 방의 원 모습을 바꾼다. */
    public record LookChanged(long countryId, long playerId, String background, String borderColor, Integer borderWidth) {}
}
