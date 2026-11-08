package com.trendist.player.service;

import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import java.util.List;

/** 플레이어 인기도(4.34): 대상마다 하루에 한 번 올리거나 내리기, 닉네임 검색. 하루는 그 나라 시간대 기준이다. */
public interface PopularityService {
    /** 투표. value는 +1 또는 -1. 자기 자신 400 POPULARITY_SELF, 다른 나라·없는 사람 404, 오늘 이미 했으면 409 POPULARITY_DONE */
    PlayerDtos.PopularityResult vote(long userId, long targetPlayerId, int value);

    /** 닉네임 검색(같은 나라, 최대 20명, 인기도 높은 순). 2글자 이상이 아니어도 1글자부터 받는다. 계정당 초당 3회 */
    List<PlayerDtos.PlayerHit> search(long userId, String query);

    /** 오늘 viewer가 그 사람에게 준 표(+1, -1, 없으면 0) */
    int myVoteToday(long userId, Player target);
}
