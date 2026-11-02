package com.trendist.profile.repository;

import com.trendist.profile.entity.Profile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Optional<Profile> findFirstByPlayerIdAndEquippedTrue(long playerId);

    Optional<Profile> findByPlayerIdAndSlot(long playerId, int slot);

    List<Profile> findByPlayerIdOrderBySlotAsc(long playerId);
}
