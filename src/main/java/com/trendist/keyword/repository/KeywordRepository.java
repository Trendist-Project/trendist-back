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

    /** AI 설명을 아직 못 받은 키워드(처음 🔥가 된 적 있고, 금지 아님, 시도 횟수가 한도 미만). 최근에 🔥가 된 것부터 */
    @Query("""
            select k.id from Keyword k
            where k.aiComment is null and k.activatedAt is not null and k.aiAttempts < :maxAttempts
              and k.keywordState.id <> :bannedId
            order by k.activatedAt desc""")
    List<Long> findIdsNeedingAiComment(@Param("bannedId") Long bannedId, @Param("maxAttempts") long maxAttempts,
                                       org.springframework.data.domain.Pageable pageable);

    /** 관리자 AI 설명 목록(요청 없이 보는 "설명 없음"과 "전체"). withComment가 true면 설명이 있는 것, false면 없는 것 */
    @Query("""
            select k from Keyword k join fetch k.keywordState
            where k.country.id = :countryId and k.activatedAt is not null and k.keywordState.id <> :bannedId
              and ((:withComment = true and k.aiComment is not null) or (:withComment = false and k.aiComment is null))""")
    org.springframework.data.domain.Page<Keyword> findForAdminAi(@Param("countryId") Long countryId, @Param("bannedId") Long bannedId,
                                                                 @Param("withComment") boolean withComment,
                                                                 org.springframework.data.domain.Pageable pageable);

    /** 한 상태의 키워드 전체와 반영 소스(1분 트렌드 값 점검). */
    @Query("select k from Keyword k join fetch k.dataSource where k.keywordState.id = :stateId")
    List<Keyword> findAllByStateWithSource(@Param("stateId") Long stateId);

    /** 그 소스를 반영 소스로 쓰는 키워드. */
    List<Keyword> findByDataSourceId(Long dataSourceId);

    /** 외부 유지 기간이 끝난 키워드(그 소스). */
    List<Keyword> findByDataSourceIdAndExternalUntilBefore(Long dataSourceId, Instant now);

    /**
     * 관리자 키워드 목록(13.6): 상태·반영 소스·검색어(정규화 키에 포함)로 거르고, 정렬은 Pageable로 받는다.
     * 비어 있는(null) 조건은 쓰지 않는다.
     */
    @Query(value = """
            select k from Keyword k join fetch k.keywordState join fetch k.dataSource
            where k.country.id = :countryId
              and (:anyState = true or k.keywordState.id in :stateIds)
              and (:anySource = true or k.dataSource.id in :sourceIds)
              and (:q is null or k.normalized like :q)""",
            countQuery = """
            select count(k) from Keyword k
            where k.country.id = :countryId
              and (:anyState = true or k.keywordState.id in :stateIds)
              and (:anySource = true or k.dataSource.id in :sourceIds)
              and (:q is null or k.normalized like :q)""")
    org.springframework.data.domain.Page<Keyword> searchForAdmin(@Param("countryId") Long countryId,
                                                                  @Param("anyState") boolean anyState, @Param("stateIds") List<Long> stateIds,
                                                                  @Param("anySource") boolean anySource, @Param("sourceIds") List<Long> sourceIds,
                                                                  @Param("q") String q,
                                                                  org.springframework.data.domain.Pageable pageable);

    /** 관리자 키워드 목록의 상태별 개수(상태 필터 없이, 소스·검색어만 적용). [상태 이름, 개수] */
    @Query("""
            select k.keywordState.name, count(k) from Keyword k
            where k.country.id = :countryId
              and (:anySource = true or k.dataSource.id in :sourceIds)
              and (:q is null or k.normalized like :q)
            group by k.keywordState.name""")
    List<Object[]> countByStateForAdmin(@Param("countryId") Long countryId, @Param("anySource") boolean anySource,
                                        @Param("sourceIds") List<Long> sourceIds, @Param("q") String q);

    /** 그 나라의 한 상태 키워드 번호(구슬 보상 추첨). */
    @Query("select k.id from Keyword k where k.country.id = :countryId and k.keywordState.id = :stateId")
    List<Long> findIdsByCountryAndState(@Param("countryId") Long countryId, @Param("stateId") Long stateId);

    /** 시간별 기록 대상(9.9): 가진 사람이 있거나, 그 시간에 행이 바뀌었거나(+, 삭제, 잃기, 상태), 검색된 키워드. */
    @Query("select k from Keyword k join fetch k.country where k.holderCount > 0 or k.updatedAt >= :since or k.id in :searched")
    List<Keyword> findLogTargets(@Param("since") Instant since, @Param("searched") java.util.Collection<Long> searched);

    @Modifying
    @Query("update Keyword k set k.searchCount = k.searchCount + :n where k.id = :id")
    int addSearchCount(@Param("id") Long id, @Param("n") long n);

    /** 관리자 대시보드(13.11): 가진 사람이 많은 키워드(그 상태만), 가진 사람·트렌드 값 순. */
    @Query("select k from Keyword k where k.country.id = :countryId and k.keywordState.id = :stateId order by k.holderCount desc, k.trendValue desc, k.id desc")
    List<Keyword> topHeld(@Param("countryId") Long countryId, @Param("stateId") Long stateId, org.springframework.data.domain.Pageable pageable);

    long countByCountryIdAndCreatedAtGreaterThanEqual(Long countryId, Instant since);

    /** 관리자 대시보드(13.11): 외부 유지 기간이 (now, until] 안에 끝나는 키워드 수. */
    @Query("select count(k) from Keyword k where k.country.id = :countryId and k.externalUntil > :now and k.externalUntil <= :until")
    long countExternalEnding(@Param("countryId") Long countryId, @Param("now") Instant now, @Param("until") Instant until);
}
