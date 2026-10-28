package com.trendist.keyword.repository;

import com.trendist.keyword.entity.DataSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    Optional<DataSource> findByName(String name);
}
