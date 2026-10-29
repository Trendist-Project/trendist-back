package com.trendist.keyword.repository;

import com.trendist.keyword.entity.Keyword;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByCountryIdAndNormalized(Long countryId, String normalized);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Keyword k where k.id = :id")
    Optional<Keyword> findByIdForUpdate(@Param("id") Long id);

    /** 외부 유지 기간이 끝난 키워드(그 소스). */
    List<Keyword> findByDataSourceIdAndExternalUntilBefore(Long dataSourceId, Instant now);

    /** 그 나라의 한 상태 키워드 번호(구슬 보상 추첨). */
    @Query("select k.id from Keyword k where k.country.id = :countryId and k.keywordState.id = :stateId")
    List<Long> findIdsByCountryAndState(@Param("countryId") Long countryId, @Param("stateId") Long stateId);

    /** 시간별 기록 대상(9.9): 가진 사람이 있거나, 그 시간에 행이 바뀌었거나(+, 삭제, 잃기, 상태), 검색된 키워드. */
    @Query("select k from Keyword k join fetch k.country where k.holderCount > 0 or k.updatedAt >= :since or k.id in :searched")
    List<Keyword> findLogTargets(@Param("since") Instant since, @Param("searched") java.util.Collection<Long> searched);

    @Modifying
    @Query("update Keyword k set k.searchCount = k.searchCount + :n where k.id = :id")
    int addSearchCount(@Param("id") Long id, @Param("n") long n);
}
