package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키워드의 AI 한 줄 설명(4.12). 키워드가 {@code keywords.ai_comment_id}로 이 행을 가리킨다(키워드 하나에 행 하나, 처음 만들기를 시도할 때 만든다).
 * 설명(comment)은 비어 있을 수 있다(만들기를 시도했지만 못 받은 경우). writtenBy는 AI / ADMIN(관리자가 고침), attempts는 자동으로 만들기를
 * 시도한 횟수(실패·모름이 반복되면 멈춘다). 게임이 자주 쓰는 keywords 행을 가볍게 두고, AI 작업은 이 테이블만 고치려고 따로 뒀다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keyword_ai_comments")
public class KeywordAiComment extends BaseTimeEntity {
    @Column(length = 200) private String comment;
    @Column(name = "written_by", length = 10) private String writtenBy;
    @Column(name = "commented_at", columnDefinition = "DATETIME(6)") private Instant commentedAt;
    @Column(nullable = false) private long attempts;

    public static KeywordAiComment create() { return new KeywordAiComment(); }

    /** 설명을 저장한다(by: AI / ADMIN). 시도 횟수는 0으로 되돌린다. */
    public void write(String comment, String by, Instant now) {
        this.comment = comment; this.writtenBy = by; this.commentedAt = now; this.attempts = 0;
    }
    public void incAttempts() { attempts++; }
    public void giveUp(long max) { attempts = max; }
}
