package com.trendist.wheel.repository;

import com.trendist.wheel.entity.WheelSpin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WheelSpinRepository extends JpaRepository<WheelSpin, Long> {
    /** 플레이어가 마지막으로 돌린 기록 */
    Optional<WheelSpin> findFirstByPlayerIdOrderByCreatedAtDesc(long playerId);
}
