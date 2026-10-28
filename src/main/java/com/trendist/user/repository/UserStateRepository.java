package com.trendist.user.repository;

import com.trendist.user.entity.UserState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStateRepository extends JpaRepository<UserState, Long> {
    Optional<UserState> findByName(String name);
}
