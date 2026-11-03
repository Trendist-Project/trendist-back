package com.trendist.game.repository;

import com.trendist.game.entity.PlayerPosition;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerPositionRepository extends JpaRepository<PlayerPosition, Long> {
    Optional<PlayerPosition> findByPlayerId(long playerId);

    List<PlayerPosition> findByPlayerIdIn(Collection<Long> playerIds);

    /** 하루가 지난 위치는 쓰지 않으니 지운다. */
    @Modifying
    @Query("delete from PlayerPosition p where p.savedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
