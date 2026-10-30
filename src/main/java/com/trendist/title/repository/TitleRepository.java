package com.trendist.title.repository;

import com.trendist.title.entity.Title;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {
    List<Title> findByEnabledTrueOrderBySortOrderAsc();

    Optional<Title> findByName(String name);
}
