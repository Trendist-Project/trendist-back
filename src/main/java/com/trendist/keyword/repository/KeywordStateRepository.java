package com.trendist.keyword.repository;

import com.trendist.keyword.entity.KeywordState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordStateRepository extends JpaRepository<KeywordState, Long> {
    Optional<KeywordState> findByName(String name);
}
