package com.trendist.user.repository;

import com.trendist.user.entity.Sanction;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {
    @Query("select s from Sanction s join fetch s.sanctionType where s.user.id = :userId order by s.id desc")
    List<Sanction> findByUser(@Param("userId") Long userId);

    /** 플레이어 번호로 찾은 계정의 그 타입 제재 끝 시각(채팅처럼 방에서 플레이어 번호만 아는 곳). */
    @Query("""
            select max(s.expiresAt) from Sanction s, Player p
            where p.id = :playerId and s.user = p.user and s.sanctionType.name = :type and s.releasedAt is null and s.expiresAt > :now""")
    Instant activeUntilForPlayer(@Param("playerId") Long playerId, @Param("type") String type, @Param("now") Instant now);

    /** 그 타입의 제재 중인 것 중 가장 늦게 끝나는 만료 시각(없으면 null). */
    @Query("""
            select max(s.expiresAt) from Sanction s
            where s.user.id = :userId and s.sanctionType.name = :type and s.releasedAt is null and s.expiresAt > :now""")
    Instant activeUntil(@Param("userId") Long userId, @Param("type") String type, @Param("now") Instant now);

    long countByCreatedAtGreaterThanEqual(Instant since);

    /** 지금 제재 중인 사용자 수(종류와 관계없이). */
    @Query("select count(distinct s.user.id) from Sanction s where s.releasedAt is null and s.expiresAt > :now")
    long countActiveUsers(@Param("now") Instant now);
}
