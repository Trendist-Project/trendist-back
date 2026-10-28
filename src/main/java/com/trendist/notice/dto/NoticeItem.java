package com.trendist.notice.dto;

import java.time.Instant;

/** 공지 목록 한 줄. edited는 등록 뒤 고쳤는지("수정됨" 표시), fresh는 최근 공지인지(NEW 표시). */
public record NoticeItem(long id, String title, String body, boolean pinned, Instant createdAt, boolean edited, boolean fresh) {}
