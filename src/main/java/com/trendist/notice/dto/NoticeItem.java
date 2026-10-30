package com.trendist.notice.dto;

import java.time.Instant;

/**
 * 공지 목록 한 줄. edited는 등록 뒤 고쳤는지("수정됨" 표시), fresh는 최근 공지인지(NEW 표시),
 * popup은 지금 팝업 기간 안인지(아직 안 본 사람에게 들어올 때 띄운다, 4.16).
 */
public record NoticeItem(long id, String title, String body, boolean pinned, Instant createdAt, boolean edited, boolean fresh,
                         boolean popup) {}
