package com.trendist.repo;

import com.trendist.domain.Level;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, Long> {
    List<Level> findAllByOrderByLevelAsc();
}
