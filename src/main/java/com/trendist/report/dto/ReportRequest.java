package com.trendist.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 신고 요청. target: COMMENT(댓글 번호) / CHAT(채팅 메시지 번호) / PLAYER(같은 방의 세션 엔티티 번호). reason은 고른 사유(없어도 된다). */
public record ReportRequest(@NotNull String target, long targetId, @Size(max = 500) String reason) {}
