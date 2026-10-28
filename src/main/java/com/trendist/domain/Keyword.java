package com.trendist.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

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
    @Column(name = "trend_value", nullable = false, precision = 5, scale = 2) private BigDecimal trendValue = BigDecimal.ZERO;
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "total_plus_count", nullable = false) private long totalPlusCount;
    @Column(name = "holder_count", nullable = false) private int holderCount;
    @Column(name = "activated_at", columnDefinition = "DATETIME(6)") private Instant activatedAt;
    @Column(name = "inactive_at", columnDefinition = "DATETIME(6)") private Instant inactiveAt;
    @Column(name = "banned_at", columnDefinition = "DATETIME(6)") private Instant bannedAt;

    protected Keyword() {}

    public Keyword(Country country, DataSource source, KeywordState state, String text, String normalized) {
        this.country = country; this.dataSource = source; this.keywordState = state;
        this.text = text; this.normalized = normalized;
    }

    public Country getCountry() { return country; }
    public DataSource getDataSource() { return dataSource; }
    public KeywordState getKeywordState() { return keywordState; }
    public String getText() { return text; }
    public String getNormalized() { return normalized; }
    public BigDecimal getTrendValue() { return trendValue; }
    public long getSearchCount() { return searchCount; }
    public long getTotalPlusCount() { return totalPlusCount; }
    public int getHolderCount() { return holderCount; }
    public Instant getActivatedAt() { return activatedAt; }

    public void setDataSource(DataSource dataSource) { this.dataSource = dataSource; }
    public void setTrendValue(BigDecimal trendValue) { this.trendValue = trendValue; }
    public void incTotalPlus() { totalPlusCount++; }
    public void incHolder() { holderCount++; }
    public void decHolder() { holderCount = Math.max(0, holderCount - 1); }
    public void addSearch(long n) { searchCount += n; }

    public void activate(KeywordState active, Instant now) {
        this.keywordState = active;
        if (activatedAt == null) activatedAt = now;
    }

    public void deactivate(KeywordState inactive, Instant now) {
        this.keywordState = inactive;
        this.inactiveAt = now;
    }
}
