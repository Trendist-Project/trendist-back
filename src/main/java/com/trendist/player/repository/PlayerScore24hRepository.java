package com.trendist.player.repository;

import com.trendist.player.entity.PlayerScore24h;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerScore24hRepository extends JpaRepository<PlayerScore24h, Long> {
    Optional<PlayerScore24h> findByPlayerIdAndHourSlot(Long playerId, int hourSlot);

    /** 그 시각 전의 마지막 기록(점수가 바뀌었는지 비교하고, 기록이 없는 시간을 이어 그릴 때 쓴다). */
    Optional<PlayerScore24h> findTopByPlayerIdAndRecordedAtBeforeOrderByRecordedAtDesc(Long playerId, Instant before);

    /** [from, to) 구간의 칸, 오래된 것부터. */
    List<PlayerScore24h> findByPlayerIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(
            Long playerId, Instant from, Instant to);
}
