package com.trendist.title.repository;

import com.trendist.title.entity.Title;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {
    List<Title> findByEnabledTrueOrderBySortOrderAsc();
}
