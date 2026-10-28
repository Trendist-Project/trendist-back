package com.trendist.player.repository;

import com.trendist.player.entity.Player;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByUserIdAndCountryId(Long userId, Long countryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :id")
    Optional<Player> findByIdForUpdate(@Param("id") Long id);

    /** 1시간 점검 대상: 보유 키워드 수가 있거나 + 행이 남은 플레이어. */
    @Query("select p.id from Player p where p.holdCount > 0 or exists (select 1 from PlayerKeyword pk where pk.player = p)")
    List<Long> findCheckTargets();

    @Query("select p from Player p join fetch p.user u where p.country.id = :countryId and u.deletedAt is null order by p.currentScore desc, p.id asc")
    List<Player> findRanking(@Param("countryId") Long countryId, Pageable pageable);
}
