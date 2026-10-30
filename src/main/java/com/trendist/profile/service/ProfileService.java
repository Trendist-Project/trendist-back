package com.trendist.profile.service;

import com.trendist.profile.dto.ProfileDtos;

/** 꾸미기(4.9): 11레벨(설정값)부터 원의 바탕색·테두리 색·테두리 두께를 바꾼다. */
public interface ProfileService {
    /** 성장 캐릭터의 정해진 바탕색. 꾸미지 않았으면 이 색이다. */
    String DEFAULT_BACKGROUND = "#3fb68b";

    ProfileDtos.Mine mine(long userId);

    /** 색·두께를 저장하고 장착한다(처음이면 프로필을 만든다). 레벨, 색 형식, 바닥과의 대비, 5초 1번을 검사한다. */
    ProfileDtos.Mine update(long userId, ProfileDtos.UpdateRequest req);

    /** 방 입장 때 쓸 장착 모습. 꾸미지 않았으면 기본 모습. */
    ProfileDtos.Look lookOf(long playerId);
}
