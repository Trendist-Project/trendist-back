package com.trendist.wheel.repository;

import com.trendist.wheel.entity.WheelSpin;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WheelSpinRepository extends JpaRepository<WheelSpin, Long> {
    Optional<WheelSpin> findByPlayerIdAndSpinDate(long playerId, LocalDate spinDate);
}
