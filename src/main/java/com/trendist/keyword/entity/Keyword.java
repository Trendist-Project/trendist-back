package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.country.entity.Country;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keywords",
        uniqueConstraints = @UniqueConstraint(name = "uk_keywords_country_normalized", columnNames = {"country_id", "normalized"}),
        indexes = @Index(name = "idx_keywords_state", columnList = "country_id, keyword_state_id, trend_value"))
public class Keyword extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_id", nullable = false) private Country country;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "data_source_id", nullable = false) private DataSource dataSource;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "keyword_state_id", nullable = false) private KeywordState keywordState;
    @Column(nullable = false, length = 20) private String text;
    @Column(nullable = false, length = 100) private String normalized;
    @Column(name = "trend_value", nullable = false) private long trendValue; // 정수 점수(4.8)
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "total_plus_count", nullable = false) private long totalPlusCount;
    @Column(name = "holder_count", nullable = false) private long holderCount;
    /** 키워드 조회수(4.28): 공개 키워드 페이지와 게임 안 상세 팝업을 연 횟수. 같은 방문자는 하루 1번, 크롤러는 제외. 메모리에 모았다가 1분마다 더한다. */
    @Column(name = "view_count", nullable = false) private long viewCount;
    @Column(name = "activated_at", columnDefinition = "DATETIME(6)") private Instant activatedAt;
    @Column(name = "inactive_at", columnDefinition = "DATETIME(6)") private Instant inactiveAt;
    @Column(name = "banned_at", columnDefinition = "DATETIME(6)") private Instant bannedAt;
    /** 목록형 외부 소스(google)로 활성이 유지되는 끝 시각. 지나면 그 소스 목록에 아직 있는지 다시 본다(10.2). */
    @Column(name = "external_until", columnDefinition = "DATETIME(6)") private Instant externalUntil;

    /** AI 한 줄 설명(4.12). 처음 만들기를 시도할 때 행이 생기고, 그 전에는 null이다. 설명 내용은 {@link KeywordAiComment}가 갖는다. */
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ai_comment_id", unique = true)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private KeywordAiComment aiComment;

    public Keyword(Country country, DataSource source, KeywordState state, String text, String normalized) {
        this.country = country; this.dataSource = source; this.keywordState = state;
        this.text = text; this.normalized = normalized;
    }

    public void setDataSource(DataSource dataSource) { this.dataSource = dataSource; }

    public void attachAiComment(KeywordAiComment row) { this.aiComment = row; }
    public void setTrendValue(long trendValue) { this.trendValue = trendValue; }
    public void incTotalPlus() { totalPlusCount++; }
    public void incHolder() { holderCount++; }
    public void decHolder() { holderCount = Math.max(0, holderCount - 1); }
    public void addSearch(long n) { searchCount += n; }

    public void activate(KeywordState active, Instant now) {
        this.keywordState = active;
        if (activatedAt == null) activatedAt = now;
    }

    /** 외부 소스 목록에 올라 그 소스로 활성 유지 기간을 정한다. */
    public void holdExternal(DataSource source, Instant until) {
        this.dataSource = source;
        this.externalUntil = until;
    }

    /** 외부 유지 기간이 끝나 내부 데이터로 내린다. 상태는 서비스가 가진 사람 수로 다시 정한다. */
    public void dropExternal(DataSource internal) {
        this.dataSource = internal;
        this.externalUntil = null;
    }

    public void deactivate(KeywordState inactive, Instant now) {
        this.keywordState = inactive;
        this.inactiveAt = now;
    }

    /** 관리자 금지(13.1): 조회·점수에서 빠진다. 보관함·댓글 데이터는 그대로 둔다. */
    public void ban(KeywordState banned, Instant now) {
        this.keywordState = banned;
        this.bannedAt = now;
    }

    /** 금지 해제: 💤비활성으로 돌아간다(4.7). */
    public void unban(KeywordState inactive, Instant now) {
        this.keywordState = inactive;
        this.bannedAt = null;
        this.inactiveAt = now;
    }
}
