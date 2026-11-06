package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 키워드 시간별 기록(9.9). 키워드당 나라 현지 시 0~23의 24칸을 매시간 덮어쓴다. 행을 지우지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "keyword_log_24h",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_log_24h", columnNames = {"keyword_id", "hour_slot"}))
public class KeywordLog24h extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "keyword_id", nullable = false) private Keyword keyword;
    @Column(name = "hour_slot", nullable = false, columnDefinition = "TINYINT") private int hourSlot; // 0~23
    /** 이 칸에 기록한 시간의 시작(UTC 정시). 24시간이 지난 칸은 조회에서 무시한다. */
    @Column(name = "recorded_at", nullable = false, columnDefinition = "DATETIME") private Instant recordedAt;
    @Column(name = "search_count", nullable = false) private long searchCount;
    @Column(name = "add_count", nullable = false) private long addCount;
    @Column(name = "holder_count", nullable = false) private long holderCount;
    @Column(name = "search_inc", nullable = false) private long searchInc;
    @Column(name = "add_inc", nullable = false) private long addInc;
    @Column(name = "holder_inc", nullable = false) private long holderInc;

    public KeywordLog24h(Keyword keyword, int hourSlot) {
        this.keyword = keyword; this.hourSlot = hourSlot;
    }

    /** 그 시간이 끝날 때의 누적값과 그 시간의 증분으로 칸을 덮어쓴다. */
    public void overwrite(Instant recordedAt, long searchCount, long addCount, long holderCount,
                          long searchInc, long addInc, long holderInc) {
        this.recordedAt = recordedAt;
        this.searchCount = searchCount; this.addCount = addCount; this.holderCount = holderCount;
        this.searchInc = searchInc; this.addInc = addInc; this.holderInc = holderInc;
    }
}
