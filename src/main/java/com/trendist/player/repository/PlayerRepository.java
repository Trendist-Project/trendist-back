package com.trendist.player.repository;

import com.trendist.player.entity.Player;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 댓글 점수를 더한다. 0 아래로 내려가지 않는다(4.12). */
    @Modifying(flushAutomatically = true)
    @Query("update Player p set p.commentScore = case when p.commentScore + :delta < 0 then 0 else p.commentScore + :delta end where p.id = :id")
    int addCommentScore(@Param("id") Long id, @Param("delta") int delta);

    /** 점수 기록 대상(4.15): 그 시간 뒤에 행이 바뀐 플레이어(점수가 바뀌면 updated_at도 바뀐다). */
    @Query("select p from Player p join fetch p.country where p.updatedAt >= :since")
    List<Player> findChangedSince(@Param("since") java.time.Instant since);

    /** 검색 횟수 +1(4.4). 일괄 UPDATE라 updated_at은 바뀌지 않는다(점수 기록 대상에 끼지 않게). */
    @Modifying
    @Query("update Player p set p.searchCount = p.searchCount + 1 where p.id = :id")
    int incSearchCount(@Param("id") Long id);
}
