package com.trendist.player.service;

import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import java.util.Optional;

/** 플레이어(사용자 × 나라): 입장, 닉네임, 현재 유행도 재합산(4.1, 4.8). */
public interface PlayerService {

    /** 방 입장에 필요한 값(트랜잭션 안에서 꺼낸다). */
    /** firstTime: 아직 키워드를 한 번도 추가하지 않은 처음 온 플레이어. 가운데 세이프존에서 시작한다(4.22). */
    /** title·background·borderColor·borderWidth는 이름표 아래 칭호와 원의 꾸미기(4.9, 4.10). */
    record JoinInfo(long playerId, long countryId, String nickname, int level, double score, boolean firstTime,
                    String title, String background, String borderColor, Integer borderWidth) {}

    /** 사용자의 현재 나라 플레이어. 마지막으로 플레이한 나라, 없으면 KR. */
    Optional<Player> findCurrent(long userId);

    Optional<JoinInfo> joinInfo(long userId);

    /** 구글이 연결되지 않은 게스트 계정인지(4.1). */
    boolean isGuest(long userId);

    /** 현재 나라 플레이어. 없으면 NO_PLAYER(닉네임을 먼저 정해야 함). */
    Player requireCurrent(long userId);

    /** 닉네임 정하기(그 나라 첫 입장). */
    Player create(long userId, String rawNickname, String countryCode);

    /** 현재 나라 닉네임 바꾸기. 마지막 변경 후 설정 간격(기본 1시간)이 지나야 한다(4.1). */
    Player changeNickname(long userId, String rawNickname);

    PlayerDtos.PlayerSummary summary(Player p);

    /**
     * 현재 유행도를 다시 합산한다(4.8): min(EXP, 100) + Σ 🔥 키워드 trend_value(모두 정수).
     * 호출하는 쪽이 players 행을 잠근 트랜잭션 안에서 부른다. 결과는 커밋 뒤 방에 전달된다.
     */
    long recompute(Player p);

    /** 다른 트랜잭션에서 한 플레이어를 잠그고 다시 합산한다(재합산 잡, 전체 점검). */
    void recomputeLocked(long playerId, boolean fixHoldCount);
}
