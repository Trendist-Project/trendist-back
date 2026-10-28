package com.trendist.repo;

import com.trendist.domain.KeywordState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordStateRepository extends JpaRepository<KeywordState, Long> {
    Optional<KeywordState> findByName(String name);
}
