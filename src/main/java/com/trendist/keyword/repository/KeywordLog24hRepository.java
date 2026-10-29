package com.trendist.keyword.repository;

import com.trendist.keyword.entity.KeywordLog24h;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordLog24hRepository extends JpaRepository<KeywordLog24h, Long> {
    Optional<KeywordLog24h> findByKeywordIdAndHourSlot(Long keywordId, int hourSlot);

    /** 그 시각 전의 마지막 기록(증분을 구할 직전 누적값). */
    Optional<KeywordLog24h> findTopByKeywordIdAndRecordedAtBeforeOrderByRecordedAtDesc(Long keywordId, Instant before);

    /** [from, to) 구간의 칸, 오래된 것부터. */
    List<KeywordLog24h> findByKeywordIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(
            Long keywordId, Instant from, Instant to);
}
