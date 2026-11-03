package com.trendist.game.repository;

import com.trendist.game.entity.RoomState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomStateRepository extends JpaRepository<RoomState, Long> {
    Optional<RoomState> findByCountryId(long countryId);
}
