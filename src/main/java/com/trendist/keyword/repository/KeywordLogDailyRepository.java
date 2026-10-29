package com.trendist.keyword.repository;

import com.trendist.keyword.entity.KeywordLogDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordLogDailyRepository extends JpaRepository<KeywordLogDaily, Long> {
    Optional<KeywordLogDaily> findByKeywordIdAndRecordDate(Long keywordId, LocalDate recordDate);

    List<KeywordLogDaily> findByKeywordIdAndRecordDateGreaterThanEqualOrderByRecordDate(Long keywordId, LocalDate from);

    @Modifying
    @Query("delete from KeywordLogDaily d where d.recordDate < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
