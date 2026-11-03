package com.trendist.keyword.repository;

import com.trendist.keyword.entity.KeywordAiRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordAiRequestRepository extends JpaRepository<KeywordAiRequest, Long> {
    boolean existsByKeywordIdAndPlayerIdAndHandledAtIsNull(long keywordId, long playerId);

    long countByHandledAtIsNull();

    /** 열린 요청이 있는 키워드: [키워드 번호, 요청 수, 마지막 요청 시각]. 요청이 많은 순 */
    @Query("""
            select r.keywordId, count(r), max(r.createdAt) from KeywordAiRequest r
            where r.handledAt is null group by r.keywordId order by count(r) desc, max(r.createdAt) desc""")
    List<Object[]> openByKeyword();

    /** 주어진 키워드들의 열린 요청 수: [키워드 번호, 요청 수, 마지막 요청 시각] */
    @Query("""
            select r.keywordId, count(r), max(r.createdAt) from KeywordAiRequest r
            where r.handledAt is null and r.keywordId in :ids group by r.keywordId""")
    List<Object[]> openCounts(@Param("ids") Collection<Long> ids);

    /** 그 키워드의 열린 요청을 모두 처리한 것으로 닫는다. */
    @Modifying
    @Query("update KeywordAiRequest r set r.handledAt = :now where r.keywordId = :keywordId and r.handledAt is null")
    int closeAll(@Param("keywordId") long keywordId, @Param("now") Instant now);
}
