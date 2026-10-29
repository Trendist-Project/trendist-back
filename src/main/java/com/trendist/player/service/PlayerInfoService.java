package com.trendist.player.service;

import com.trendist.player.dto.PlayerDtos;

/** 같은 방 다른 플레이어 정보 팝업(4.11). 세션 엔티티 번호로 가리키고, 내가 접속한 방에 있는 사람만 볼 수 있다. */
public interface PlayerInfoService {
    /** 정보 카드. 계정당 초당 2회(9.5). 그 방에 없는 번호면 404 PLAYER_NOT_IN_ROOM. */
    PlayerDtos.PlayerCard card(long userId, int entityNo);

    /** 그 플레이어의 최근 7일 점수 추이. */
    PlayerDtos.ScoreHistory week(long userId, int entityNo);
}
