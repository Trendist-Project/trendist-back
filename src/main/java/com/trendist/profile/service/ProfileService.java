package com.trendist.profile.service;

import com.trendist.profile.dto.ProfileDtos;
import java.util.Optional;

/**
 * 꾸미기 프로필(4.9): 1~3번 칸에 모습(색·테두리·이름표·원 안에 그릴 것과 그림)과 효과(이동·테두리)를 저장하고, 한 칸을 장착한다.
 * 꾸미기는 해금 레벨(기본 11)부터이고, 그림과 효과는 그중에서도 Google로 로그인한 사용자만. 장착한 칸을 저장하면 방에 바로 반영된다.
 */
public interface ProfileService {
    ProfileDtos.Box box(long userId);

    /** 한 칸 저장. 레벨·색·그림·효과를 검사하고 5초에 1번(그림은 설정 간격). */
    ProfileDtos.Box save(long userId, int slot, ProfileDtos.SaveRequest req);

    /** 장착할 칸을 고른다. 5초에 1번. */
    ProfileDtos.Box equip(long userId, int slot);

    /** 장착한 칸의 모습(관리자 화면). */
    ProfileDtos.Look lookOf(long playerId);

    /** 픽셀 그림 PNG(32×32). 그림이 없는 칸이면 비어 있다. */
    Optional<byte[]> artPng(long profileId);

    /** 장착한 칸이 원 안에 그리는 그림(관리자 화면·자랑하기 카드). 없으면 비어 있다. */
    Optional<ProfileDtos.Art> equippedArt(long playerId);

    /** 관리자 꾸미기 초기화(13.10): 프로필을 모두 지워 기본 모습(1번 칸)으로 되돌린다. */
    void adminReset(long playerId);
}
