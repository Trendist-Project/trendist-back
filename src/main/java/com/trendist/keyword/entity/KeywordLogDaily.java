package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 키워드 하루별 기록(9.9). 나라 현지 날짜 하루에 한 행, 180일 보관. 매시간 그날의 시간별 칸에서 다시 만든다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keyword_log_dailies",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_log_dailies", columnNames = {"keyword_id", "record_date"}),
        indexes = @Index(name = "idx_keyword_log_dailies_date", columnList = "record_date"))
public class KeywordLogDaily extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "keyword_id", nullable = false) private Keyword keyword;
    @Column(name = "record_date", nullable = false) private LocalDate recordDate;
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "total_plus_count", nullable = false) private long totalPlusCount;
    @Column(name = "holder_count", nullable = false) private int holderCount;
    @Column(name = "search_inc", nullable = false) private long searchInc;
    @Column(name = "total_plus_inc", nullable = false) private long totalPlusInc;
    @Column(name = "holder_inc", nullable = false) private int holderInc;

    public KeywordLogDaily(Keyword keyword, LocalDate recordDate) {
        this.keyword = keyword; this.recordDate = recordDate;
    }

    /** 그날 마지막 누적값과 그날의 증분 합으로 다시 쓴다. */
    public void overwrite(long searchCount, long totalPlusCount, int holderCount, long searchInc, long totalPlusInc, int holderInc) {
        this.searchCount = searchCount; this.totalPlusCount = totalPlusCount; this.holderCount = holderCount;
        this.searchInc = searchInc; this.totalPlusInc = totalPlusInc; this.holderInc = holderInc;
    }
}
