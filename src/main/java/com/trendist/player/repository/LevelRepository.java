package com.trendist.player.repository;

import com.trendist.player.entity.Level;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, Long> {
    List<Level> findAllByOrderByLevelAsc();
}
