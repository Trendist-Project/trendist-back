package com.trendist.keyword.repository;

import com.trendist.keyword.entity.PlayerKeyword;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerKeywordRepository extends JpaRepository<PlayerKeyword, Long> {
    Optional<PlayerKeyword> findByPlayerIdAndKeywordId(Long playerId, Long keywordId);

    @Query("""
            select pk from PlayerKeyword pk join fetch pk.keyword k join fetch k.keywordState
            where pk.player.id = :playerId and pk.expiresAt > :now and k.keywordState.id <> :bannedStateId
            order by pk.createdAt desc""")
    List<PlayerKeyword> findActiveByPlayer(@Param("playerId") Long playerId, @Param("now") Instant now,
                                           @Param("bannedStateId") Long bannedStateId);

    /** 현재 유행도의 키워드 점수 부분: 🔥활성 키워드의 trend_value 합(4.8). */
    @Query("""
            select coalesce(sum(k.trendValue), 0) from PlayerKeyword pk join pk.keyword k
            where pk.player.id = :playerId and pk.expiresAt > :now and k.keywordState.id = :activeStateId""")
    BigDecimal sumActiveTrendValue(@Param("playerId") Long playerId, @Param("now") Instant now,
                                   @Param("activeStateId") Long activeStateId);

    @Query("select count(pk) from PlayerKeyword pk where pk.player.id = :playerId and pk.expiresAt > :now")
    long countUnexpired(@Param("playerId") Long playerId, @Param("now") Instant now);

    @Query("select pk.player.id from PlayerKeyword pk where pk.keyword.id = :keywordId and pk.expiresAt > :now")
    List<Long> findHolderPlayerIds(@Param("keywordId") Long keywordId, @Param("now") Instant now);

    @Query("select pk.id from PlayerKeyword pk where pk.expiresAt <= :now order by pk.expiresAt")
    List<Long> findExpiredIds(@Param("now") Instant now, Pageable pageable);

    /** 잃는 후보: 만료 전이고 금지되지 않은 키워드(4.3). */
    @Query("""
            select pk from PlayerKeyword pk join fetch pk.keyword k
            where pk.player.id = :playerId and pk.expiresAt > :now and k.keywordState.id <> :bannedStateId""")
    List<PlayerKeyword> findLosableByPlayer(@Param("playerId") Long playerId, @Param("now") Instant now,
                                            @Param("bannedStateId") Long bannedStateId);

    @Modifying
    @Query("delete from PlayerKeyword pk where pk.id = :id and pk.expiresAt <= :now")
    int deleteIfExpired(@Param("id") Long id, @Param("now") Instant now);
}
