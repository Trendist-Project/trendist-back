package com.trendist.notice.dto;

import java.time.Instant;

/** 관리자 공지 작성·수정(4.16, 13). */
public final class AdminNoticeDtos {
    private AdminNoticeDtos() {}

    /** popupDays: 팝업 기간(1~90일, 등록 시각부터). popupEnabled가 false면 무시한다. */
    public record Request(String title, String body, boolean pinned, boolean popupEnabled, Integer popupDays) {}

    /** 관리자 목록 한 줄. popupDays는 등록 시각부터 popupUntil까지의 일수(수정 화면에 다시 채운다). */
    public record Item(long id, String title, String body, boolean pinned, boolean popupEnabled, Instant popupUntil,
                       Integer popupDays, Instant createdAt, Instant updatedAt, boolean edited) {}
}
