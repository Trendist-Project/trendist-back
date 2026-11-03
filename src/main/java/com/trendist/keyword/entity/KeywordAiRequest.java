package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 설명 갱신 요청(4.12): 사용자가 "이 설명이 맞지 않아요"를 눌러 접수한 것. 관리자가 AI에게 다시 받거나 직접 고치거나 닫으면
 * handledAt이 채워진다. 같은 사람이 같은 키워드에 열린 요청을 하나만 가진다(서비스가 확인).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keyword_ai_requests", indexes = {
        @Index(name = "idx_kw_ai_req_keyword", columnList = "keyword_id, handled_at"),
        @Index(name = "idx_kw_ai_req_player", columnList = "player_id, keyword_id")})
public class KeywordAiRequest extends BaseTimeEntity {
    @Column(name = "keyword_id", nullable = false) private long keywordId;
    @Column(name = "player_id", nullable = false) private long playerId;
    @Column(name = "handled_at", columnDefinition = "DATETIME(6)") private Instant handledAt;

    public KeywordAiRequest(long keywordId, long playerId) { this.keywordId = keywordId; this.playerId = playerId; }
}
