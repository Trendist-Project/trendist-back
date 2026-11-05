package com.trendist.emergency.dto;

import java.time.Instant;

/** 긴급 안내(4.29). */
public final class EmergencyDtos {
    private EmergencyDtos() {}

    /** 지금 보여 줄 긴급 안내. remainingSeconds는 서버 시계 기준 남은 시간이라 클라이언트 시계가 틀려도 끝 시각을 맞출 수 있다. */
    public record Active(long id, String message, String messageEn, Instant endsAt, long remainingSeconds) {}

    /** 공개 조회 응답: notice가 null이면 지금 보여 줄 것이 없다. */
    public record Current(Active notice) {}

    /** 관리자가 올리는 값: 한 줄 안내(최대 200자), 영어판(선택), 보일 시간(분, 1~720). */
    public record Request(String message, String messageEn, Integer minutes) {}

    /** 관리자 목록 한 줄. status: ACTIVE(보이는 중) / ENDED(시간이 끝남) / CANCELED(관리자가 끝냄) */
    public record Item(long id, String message, String messageEn, Instant createdAt, Instant endsAt, Instant canceledAt, String status, long minutes) {}
}
