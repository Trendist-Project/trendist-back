package com.trendist.title.repository;

import com.trendist.title.entity.PlayerTitle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerTitleRepository extends JpaRepository<PlayerTitle, Long> {
    List<PlayerTitle> findByPlayerId(long playerId);

    boolean existsByPlayerIdAndTitleId(long playerId, long titleId);
}
