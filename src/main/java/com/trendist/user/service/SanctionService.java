package com.trendist.user.service;

import java.time.Instant;

/** 사용자 제재 확인(13.5). 거는 것과 푸는 것은 관리자 사용자 관리(AdminUserService)가 한다. */
public interface SanctionService {
    /** 그 타입(SanctionType 이름)으로 제재 중이면 끝나는 시각, 아니면 null. */
    Instant activeUntil(long userId, String type);

    /** 댓글 작성 정지면 403 COMMENT_BANNED. */
    void requireCanComment(long userId);

    /** 채팅 정지 중이면 끝나는 시각(플레이어 번호로, 15초 캐시). 아니면 null. */
    Instant chatBannedUntil(long playerId);

    /** 제재를 걸거나 풀었을 때 캐시를 비운다. */
    void evictCache();

    /** 끝나는 시각을 "M월 d일 HH:mm까지" 또는 "영구"로 */
    String untilText(Instant until);

    /** 영어판: "permanent" 또는 "until Nov 4, 12:00 UTC" */
    String untilText(Instant until, com.trendist.common.support.Lang lang);
}
