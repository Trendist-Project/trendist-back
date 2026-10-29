package com.trendist.player.repository;

import com.trendist.player.entity.PlayerScoreDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerScoreDailyRepository extends JpaRepository<PlayerScoreDaily, Long> {
    Optional<PlayerScoreDaily> findByPlayerIdAndRecordDate(Long playerId, LocalDate recordDate);

    /** 그날 전의 마지막 기록. */
    Optional<PlayerScoreDaily> findTopByPlayerIdAndRecordDateBeforeOrderByRecordDateDesc(Long playerId, LocalDate before);

    List<PlayerScoreDaily> findByPlayerIdAndRecordDateGreaterThanEqualOrderByRecordDate(Long playerId, LocalDate from);

    Optional<PlayerScoreDaily> findTopByPlayerIdOrderByRecordDateAsc(Long playerId);
}
