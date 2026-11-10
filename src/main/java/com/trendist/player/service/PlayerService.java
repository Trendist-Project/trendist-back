package com.trendist.player.service;

import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import java.util.Optional;

/** 플레이어(사용자 × 나라): 입장, 닉네임, 현재 유행도 재합산(4.1, 4.8). */
public interface PlayerService {

    /** 방 입장에 필요한 값(트랜잭션 안에서 꺼낸다). */
    /** title·skin·trail은 이름표 아래 칭호, 원의 꾸미기, 이동 효과 코드(4.9, 4.10, 4.23). */
    record JoinInfo(long playerId, long countryId, String nickname, int level, double score,
                    String title, com.trendist.game.room.Room.Skin skin, String trail) {}

    /** 사용자의 현재 나라 플레이어. 마지막으로 플레이한 나라, 없으면 KR. */
    Optional<Player> findCurrent(long userId);

    Optional<JoinInfo> joinInfo(long userId);

    /** 구글이 연결되지 않은 게스트 계정인지(4.1). */
    boolean isGuest(long userId);

    /** 현재 나라 플레이어. 없으면 NO_PLAYER(닉네임을 먼저 정해야 함). */
    Player requireCurrent(long userId);

    /** 닉네임 정하기(그 나라 첫 입장). */
    /**
     * 사용자가 고른 나라로 마지막 플레이 지역을 맞추고 그 나라의 플레이어를 돌려준다(없으면 비어 있음: 닉네임 만들기).
     * 없거나 닫힌 나라면 400.
     */
    Optional<Player> selectCountry(long userId, String countryCode);

    Player create(long userId, String rawNickname, String countryCode);

    /** 현재 나라 닉네임 바꾸기. 마지막 변경 후 설정 간격(기본 5분)이 지나야 한다(4.1). */
    Player changeNickname(long userId, String rawNickname);

    PlayerDtos.PlayerSummary summary(Player p);

    /**
     * 현재 유행도를 다시 합산한다(4.8): min(EXP, 100) + Σ 🔥 키워드 trend_value(모두 정수).
     * 호출하는 쪽이 players 행을 잠근 트랜잭션 안에서 부른다. 결과는 커밋 뒤 방에 전달된다.
     */
    long recompute(Player p);

    /** 다른 트랜잭션에서 한 플레이어를 잠그고 다시 합산한다(재합산 잡, 전체 점검). */
    void recomputeLocked(long playerId, boolean fixHoldCount);

    /** 죽였다(4.31): 죽인 횟수 +1 하고 칭호를 확인한다. 방(틱) 밖의 작업 스레드에서 부른다. */
    void recordKill(long playerId);

    /** 공격에 성공했다(4.3): 빼앗은 횟수 +1(불꽃으로 키워드를 모두 잃게 해도 1). 칭호를 확인한다. 방(틱) 밖의 작업 스레드에서 부른다. */
    void recordSteal(long playerId);

    /** 행을 이미 잠근 트랜잭션 안에서: 공격 성공 1회(빼앗은 횟수 +1, EXP `attack_exp`)를 반영하고 점수·레벨을 다시 정한다(4.3). */
    void awardAttack(Player p);

    /** 현재 유행도가 같은 원과 부딪혔다(4.3): 횟수 +1 하고 칭호를 확인한다. 행을 잠그지 않는다. 방(틱) 밖의 작업 스레드에서 부른다. */
    void recordTie(long playerId);

    /** 패배했다(키워드가 없는 채로 져서 죽음, 4.31): 패배 횟수 +1 하고 칭호를 확인한다. 행을 잠그지 않는다. 방(틱) 밖의 작업 스레드에서 부른다. */
    void recordDefeat(long playerId);

    /** 연속 빼앗기 콤보가 combo가 됐다(4.25): 최고 기록보다 높을 때만 올리고 칭호를 확인한다 */
    void recordCombo(long playerId, int combo);

    /** 상대의 콤보 불꽃을 꺼뜨렸다(4.25): 횟수를 올리고 칭호를 확인한다 */
    void recordFlameOut(long playerId);

    /** 행을 이미 잠근 트랜잭션 안에서: 죽였다(죽인 횟수 +1, EXP `kill_exp`)를 반영하고 점수·레벨을 다시 정한다(4.31). */
    void awardKill(Player p);

    /** 몬스터(보스) 보상(4.35): 행을 잠그고 EXP(고정 exp + 그 사람의 "현재 레벨의 폭(그 레벨에 오를 때 필요했던 EXP, 1레벨은 다음 레벨로 가는 폭)"의 levelPercent%)를 더하고(실제로 더한 EXP를 돌려준다) 처치 참여 횟수를 올린 뒤(cleared일 때만) 점수·레벨을 다시 정한다. 방(틱) 밖의 작업 스레드에서 부른다. */
    long awardMonster(long playerId, long exp, double levelPercent, boolean cleared);
}
